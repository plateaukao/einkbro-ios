package info.plateaukao.einkbro.backup

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.database.HistoryRecord
import info.plateaukao.einkbro.epub.ZipReader
import info.plateaukao.einkbro.epub.ZipWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * App-data + bookmark backup (parity Phase J). A backup is a ZIP built with the
 * same pure-Kotlin [ZipWriter] used for EPUBs: `prefs.json` (all `sp_`-prefixed
 * NSUserDefaults), `bookmarks.json`, and `history.json`. Restore reads those back
 * through the prefs store and the Room DAOs — no raw DB-file swap, so it applies
 * live without corrupting the open database.
 *
 * The Android backup serializes per-table JSON too; this covers the core user
 * data (prefs, bookmarks, history) and reuses the DAO overwrite paths.
 */
object BackupManager {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val bookmarkManager get() = AppServices.bookmarkManager
    private val historyDao get() = AppServices.database.historyDao()

    // --- bookmarks (JSON export; JSON or Netscape-HTML import) ---

    suspend fun exportBookmarksJson(): String {
        val dtos = bookmarkManager.getAllBookmarks().map {
            BookmarkDto(it.title, it.url, it.isDirectory, it.parent, it.order, it.id)
        }
        return json.encodeToString(ListSerializer(BookmarkDto.serializer()), dtos)
    }

    suspend fun importBookmarks(text: String) {
        val bookmarks = if (text.trimStart().startsWith("[")) {
            json.decodeFromString(ListSerializer(BookmarkDto.serializer()), text).map { it.toBookmark() }
        } else {
            parseNetscape(text)
        }
        if (bookmarks.isNotEmpty()) bookmarkManager.overwriteBookmarks(bookmarks)
    }

    /** Minimal Netscape/Chrome bookmark parser: every `<A HREF="…">title</A>` (flat). */
    // Netscape/Chrome bookmark HTML with nested folders (Android
    // BackupUnit.parseChromeBookmarks): <H3> opens a folder whose children live
    // in the following <DL>, so we track a parent-id stack instead of Jsoup.
    private fun parseNetscape(html: String): List<Bookmark> {
        val tokenRegex = Regex(
            """<h3[^>]*>(.*?)</h3>|<a[^>]*\shref="([^"]*)"[^>]*>(.*?)</a>|<dl[^>]*>|</dl>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val strip = Regex("<[^>]*>")
        fun clean(s: String) = s.replace(strip, "").trim()

        var counter = 0
        val parentStack = ArrayDeque<Int>().apply { addLast(0) }
        var pendingFolder: Int? = null
        val result = mutableListOf<Bookmark>()

        for (m in tokenRegex.findAll(html)) {
            val tag = m.value.lowercase()
            when {
                tag.startsWith("<h3") -> {
                    val id = ++counter
                    result += Bookmark(
                        title = clean(m.groupValues[1]),
                        url = "",
                        isDirectory = true,
                        parent = parentStack.last(),
                        order = result.size,
                    ).also { it.id = id }
                    pendingFolder = id
                }
                tag.startsWith("<a") -> {
                    val url = m.groupValues[2].trim()
                    if (url.isNotBlank()) {
                        result += Bookmark(
                            title = clean(m.groupValues[3]).ifBlank { url },
                            url = url,
                            parent = parentStack.last(),
                            order = result.size,
                        ).also { it.id = ++counter }
                    }
                }
                tag.startsWith("<dl") -> {
                    parentStack.addLast(pendingFolder ?: parentStack.last())
                    pendingFolder = null
                }
                tag == "</dl>" -> if (parentStack.size > 1) parentStack.removeLast()
            }
        }
        return result
    }

    // --- per-site domain configurations (translation/CSS/JS/font per host) ---

    private suspend fun exportDomainConfigsJson(): String {
        val configs = AppServices.bookmarkManager.getAllDomainConfigurations()
        return json.encodeToString(
            ListSerializer(
                info.plateaukao.einkbro.database.DomainConfigurationData.serializer()
            ),
            configs,
        )
    }

    private fun importDomainConfigs(text: String) {
        val configs = json.decodeFromString(
            ListSerializer(
                info.plateaukao.einkbro.database.DomainConfigurationData.serializer()
            ),
            text,
        )
        configs.forEach { AppServices.bookmarkManager.addDomainConfiguration(it) }
    }

    // --- history ---

    private suspend fun exportHistoryJson(): String {
        val dtos = historyDao.getAllHistory().map { HistoryDto(it.TITLE, it.URL, it.TIME) }
        return json.encodeToString(ListSerializer(HistoryDto.serializer()), dtos)
    }

    private suspend fun importHistory(text: String) {
        val dtos = runCatching {
            json.decodeFromString(ListSerializer(HistoryDto.serializer()), text)
        }.getOrNull() ?: return
        historyDao.deleteAll()
        dtos.forEach { historyDao.insert(HistoryRecord(it.title, it.url, it.time)) }
    }

    // --- full backup zip ---

    suspend fun exportBackupZip(): ByteArray {
        val zip = ZipWriter()
        zip.addStored("manifest.json", """{"version":1,"app":"einkbro-ios"}""")
        zip.addStored("prefs.json", AppServices.sharedPreferences.exportPrefs())
        zip.addStored("bookmarks.json", exportBookmarksJson())
        zip.addStored("history.json", exportHistoryJson())
        zip.addStored("domain_configs.json", exportDomainConfigsJson())
        return zip.build()
    }

    /** Restores prefs + bookmarks + history from a backup zip (iOS format or an
     *  Android BackupUnit v2 zip); returns false when nothing restorable was found. */
    suspend fun importBackupZip(bytes: ByteArray): Boolean {
        val entries = ZipReader.read(bytes) ?: return false
        var restored = false
        // iOS-format entries. Android's bookmarks.json / history.json share these
        // names with identical JSON shapes, so both platforms restore through them.
        entries["prefs.json"]?.let {
            AppServices.sharedPreferences.importPrefs(it.decodeToString()); restored = true
        }
        entries["bookmarks.json"]?.let { importBookmarks(it.decodeToString()); restored = true }
        entries["history.json"]?.let { importHistory(it.decodeToString()); restored = true }
        entries["domain_configs.json"]?.let { importDomainConfigs(it.decodeToString()); restored = true }
        // Android-only entries (BackupUnit v2): raw SharedPreferences XML, the flat
        // GPT-settings JSON, and per-site configs inside database_data.json.
        entries.filterKeys { it.startsWith("shared_prefs/") && it.endsWith(".xml") }
            .forEach { (_, xml) -> if (importAndroidPrefsXml(xml.decodeToString())) restored = true }
        entries["gpt_settings.json"]?.let {
            if (importFlatPrefsJson(it.decodeToString())) restored = true
        }
        entries["database_data.json"]?.let {
            if (importAndroidDatabaseData(it.decodeToString())) restored = true
        }
        // Config delegates read the prefs store live, so restored settings apply on
        // next read; a relaunch guarantees everything (incl. cached sub-configs).
        return restored
    }

    // --- Android BackupUnit v2 compatibility ---

    // Never carry another device's Drive OAuth session across a restore.
    // The e-ink image prefs are Android-only tuning for e-ink panels; iOS
    // has no e-ink display and no UI for them, so importing them would
    // silently distort image colors.
    private fun isPrivateKey(key: String) =
        key == "sp_drive_auth_state" || key == "sp_drive_pending_auth" ||
                key == "sp_image_adjustment" || key == "sp_eink_image_mode"

    /** Android SharedPreferences XML (`<map><boolean name=… value=…/>…</map>`). */
    private fun importAndroidPrefsXml(xml: String): Boolean {
        val prefs = AppServices.sharedPreferences
        var imported = false
        Regex("""<(boolean|int|long|float)\s+name="([^"]*)"\s+value="([^"]*)"\s*/>""")
            .findAll(xml).forEach { m ->
                val (type, key, value) = m.destructured
                if (isPrivateKey(key)) return@forEach
                runCatching {
                    when (type) {
                        "boolean" -> prefs.edit().putBoolean(key, value.toBooleanStrict()).apply()
                        "int" -> prefs.edit().putInt(key, value.toInt()).apply()
                        "long" -> prefs.edit().putLong(key, value.toLong()).apply()
                        else -> prefs.edit().putFloat(key, value.toFloat()).apply()
                    }
                    imported = true
                }
            }
        Regex("""<string\s+name="([^"]*)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { m ->
                val key = m.groupValues[1]
                if (isPrivateKey(key)) return@forEach
                prefs.edit().putString(key, unescapeXml(m.groupValues[2])).apply()
                imported = true
            }
        Regex("""<set\s+name="([^"]*)">(.*?)</set>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { m ->
                val key = m.groupValues[1]
                if (isPrivateKey(key)) return@forEach
                val values = Regex("<string>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(m.groupValues[2]).map { unescapeXml(it.groupValues[1]) }.toSet()
                prefs.edit().putStringSet(key, values).apply()
                imported = true
            }
        return imported
    }

    private fun unescapeXml(s: String): String = s
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    /** Flat `{key: typed value}` prefs JSON (Android's gpt_settings.json). */
    private fun importFlatPrefsJson(text: String): Boolean {
        val obj = runCatching {
            Json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return false
        val prefs = AppServices.sharedPreferences
        var imported = false
        obj.forEach { (key, element) ->
            if (isPrivateKey(key)) return@forEach
            val prim = element as? JsonPrimitive ?: return@forEach
            when {
                prim.isString -> prefs.edit().putString(key, prim.content).apply()
                prim.booleanOrNull != null -> prefs.edit().putBoolean(key, prim.boolean).apply()
                prim.longOrNull != null -> {
                    val long = prim.long
                    if (long in Int.MIN_VALUE..Int.MAX_VALUE) {
                        prefs.edit().putInt(key, long.toInt()).apply()
                    } else prefs.edit().putLong(key, long).apply()
                }
                prim.floatOrNull != null -> prefs.edit().putFloat(key, prim.float).apply()
                else -> return@forEach
            }
            imported = true
        }
        return imported
    }

    /** Per-site configs from Android's database_data.json ("domain_configurations":
     *  [{domain, configuration}] where configuration is the same serialized
     *  DomainConfigurationData both platforms use). */
    private fun importAndroidDatabaseData(text: String): Boolean {
        val configs = runCatching {
            Json.parseToJsonElement(text).jsonObject["domain_configurations"]?.jsonArray
        }.getOrNull() ?: return false
        var imported = false
        configs.forEach { element ->
            runCatching {
                val configJson = element.jsonObject["configuration"]?.jsonPrimitive?.content
                    ?: return@forEach
                val data = json.decodeFromString(
                    info.plateaukao.einkbro.database.DomainConfigurationData.serializer(),
                    configJson,
                )
                AppServices.bookmarkManager.addDomainConfiguration(data)
                imported = true
            }
        }
        return imported
    }

    @Serializable
    private class BookmarkDto(
        val title: String,
        val url: String,
        val isDirectory: Boolean = false,
        val parent: Int = 0,
        val order: Int = 0,
        val id: Int = 0,
    ) {
        fun toBookmark() = Bookmark(title, url, isDirectory, parent, order).also { it.id = id }
    }

    @Serializable
    private class HistoryDto(val title: String, val url: String, val time: Long)
}
