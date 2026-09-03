package info.plateaukao.einkbro.backup

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.Article
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.database.ChatGptQuery
import info.plateaukao.einkbro.database.ChatSession
import info.plateaukao.einkbro.database.DomainConfigurationData
import info.plateaukao.einkbro.database.FaviconInfo
import info.plateaukao.einkbro.database.Highlight
import info.plateaukao.einkbro.database.HistoryRecord
import info.plateaukao.einkbro.database.VideoTranscript
import info.plateaukao.einkbro.epub.ZipReader
import info.plateaukao.einkbro.epub.ZipWriter
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.backup_category_all_preferences
import info.plateaukao.einkbro.resources.backup_category_bookmarks
import info.plateaukao.einkbro.resources.backup_category_chat_sessions
import info.plateaukao.einkbro.resources.backup_category_database_data
import info.plateaukao.einkbro.resources.backup_category_gpt_settings
import info.plateaukao.einkbro.resources.backup_category_history
import info.plateaukao.einkbro.resources.backup_category_transcripts
import info.plateaukao.einkbro.resources.backup_category_userscripts
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * App-data + bookmark backup (parity Phase J), aligned with Android's
 * `BackupUnit` v2 zip so the two apps can read each other's files — including
 * the one the Android app keeps in Google Drive (`einkbro-backup.zip`).
 *
 * **Layout written** (all entries Android knows, plus `prefs.json`):
 *
 * | entry | contents |
 * |---|---|
 * | `_manifest.json` | `{"version":2,"app":"einkbro-ios","categories":[…]}` |
 * | `prefs.json` | every portable `sp_` pref (iOS-only; Android ignores it) |
 * | `gpt_settings.json` | the Gen-AI subset of prefs, flat typed JSON (Android's GPT_SETTINGS) |
 * | `bookmarks.json` / `history.json` | same JSON shapes on both platforms |
 * | `database_data.json` | articles, highlights, chat_gpt_queries, domain_configurations (favicons are read from old backups but no longer written: re-fetchable, and they dominated the size) |
 * | `userscripts/NAME.user.js` + `userscripts/userscripts.json` | script bodies + enabled/sourceUrl/GM values |
 * | `transcripts.json` / `chat_sessions.json` | only when non-empty |
 *
 * **Restore is append-only.** Nothing local is ever deleted or replaced:
 * prefs fill in only keys this device has never set, bookmarks/folders and
 * every DB table merge on content keys (Android's `mergeBookmarks` /
 * `restoreDatabaseData` rules), per-site rules merge field by field with the
 * local value winning, userscripts skip an already-installed `@name`. History
 * appends rows not present (Android replaces it; append is the safer reading
 * of "restore onto a device that is already in use"). Re-restoring the same
 * backup adds nothing.
 *
 * Rule for anything file-backed: **if the raw bytes are not in the zip, the list
 * that points at them does not go in either.** A backup carries no .webarchive /
 * .mht / .epub / .pdf payloads, so the read-later list, `saved_pages` and the
 * saved EPUB/PDF lists are excluded in both directions.
 */
/** Android BackupUnit.BackupCategory: what the backup/restore pickers offer. */
enum class BackupCategory(val displayNameRes: org.jetbrains.compose.resources.StringResource) {
    ALL_PREFERENCES(Res.string.backup_category_all_preferences),
    GPT_SETTINGS(Res.string.backup_category_gpt_settings),
    BOOKMARKS(Res.string.backup_category_bookmarks),
    HISTORY(Res.string.backup_category_history),
    DATABASE_DATA(Res.string.backup_category_database_data),
    USERSCRIPTS(Res.string.backup_category_userscripts),
    TRANSCRIPTS(Res.string.backup_category_transcripts),
    CHAT_SESSIONS(Res.string.backup_category_chat_sessions),
}

@OptIn(ExperimentalEncodingApi::class)
object BackupManager {

    // coerceInputValues: Android's DomainConfigurationData serialises its
    // unset booleans as null, which must decode to this side's `false`.
    private val json = Json {
        ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false
        coerceInputValues = true
    }
    private val prefs get() = AppServices.sharedPreferences
    private val bookmarkManager get() = AppServices.bookmarkManager
    private val database get() = AppServices.database
    private val historyDao get() = database.historyDao()

    /** What a restore added; every count is *new* rows, never overwrites. */
    class RestoreSummary {
        var prefs = 0
        var bookmarks = 0
        var history = 0
        var siteRules = 0
        var favicons = 0
        var articles = 0
        var highlights = 0
        var gptQueries = 0
        var userscripts = 0
        var transcripts = 0
        var chatSessions = 0

        /** At least one entry in the zip was recognised as EinkBro data. */
        var recognized = false

        val total: Int
            get() = prefs + bookmarks + history + siteRules + favicons + articles +
                highlights + gptQueries + userscripts + transcripts + chatSessions

        /** Human-readable "Added 12 bookmarks, 3 site rules, 40 settings". */
        fun describe(): String {
            val parts = listOfNotNull(
                bookmarks.takeIf { it > 0 }?.let { "$it bookmarks" },
                history.takeIf { it > 0 }?.let { "$it history entries" },
                prefs.takeIf { it > 0 }?.let { "$it settings" },
                siteRules.takeIf { it > 0 }?.let { "$it site rules" },
                favicons.takeIf { it > 0 }?.let { "$it favicons" },
                articles.takeIf { it > 0 }?.let { "$it articles" },
                highlights.takeIf { it > 0 }?.let { "$it highlights" },
                gptQueries.takeIf { it > 0 }?.let { "$it AI queries" },
                userscripts.takeIf { it > 0 }?.let { "$it userscripts" },
                transcripts.takeIf { it > 0 }?.let { "$it transcripts" },
                chatSessions.takeIf { it > 0 }?.let { "$it chat sessions" },
            )
            return if (parts.isEmpty()) "Nothing new to restore — everything was already here"
            else "Added " + parts.joinToString(", ")
        }
    }

    // --- bookmarks (JSON export; JSON or Netscape-HTML import) ---

    suspend fun exportBookmarksJson(): String {
        val dtos = bookmarkManager.getAllBookmarks().map {
            BookmarkDto(it.title, it.url, it.isDirectory, it.parent, it.order, it.id)
        }
        return json.encodeToString(ListSerializer(BookmarkDto.serializer()), dtos)
    }

    /** Merges bookmarks from JSON (ours / Android's) or Netscape HTML; returns the number added. */
    suspend fun importBookmarks(text: String): Int {
        val bookmarks = if (text.trimStart().startsWith("[")) {
            runCatching {
                json.decodeFromString(ListSerializer(BookmarkDto.serializer()), text).map { it.toBookmark() }
            }.getOrDefault(emptyList())
        } else {
            parseNetscape(text)
        }
        return if (bookmarks.isEmpty()) 0 else mergeBookmarks(bookmarks)
    }

    /**
     * Android BackupUnit.mergeBookmarks: imported ids are per-device
     * autoincrements and mean nothing here, so folders match by title within
     * the same (already mapped) parent — created with fresh ids when missing —
     * and bookmarks match by url within their mapped folder. Nothing local is
     * deleted and re-restoring the same backup adds nothing.
     */
    private suspend fun mergeBookmarks(imported: List<Bookmark>): Int {
        val dao = database.bookmarkDao()
        val local = dao.getAllBookmarks()
        val localFolders = HashMap<Pair<Int, String>, Int>()
        local.filter { it.isDirectory }.forEach { localFolders[it.parent to it.title] = it.id }
        val localUrls = local.filterNot { it.isDirectory }.map { it.parent to it.url }.toHashSet()
        var added = 0

        // Map imported folder ids to local ones, walking parents before children.
        // A non-positive id would collide with the id-0 root sentinel, so such a
        // (corrupt) folder is not created; an unknown parent falls back to root.
        val folders = imported.filter { it.isDirectory && it.id > 0 }
        val importedFolderIds = folders.map { it.id }.toHashSet()
        val idMap = HashMap<Int, Int>().apply { put(0, 0) }
        val pending = folders.toMutableList()
        while (pending.isNotEmpty()) {
            val ready = pending
                .filter { it.parent in idMap || it.parent !in importedFolderIds }
                .ifEmpty { pending.toList() }
            for (folder in ready) {
                val localParent = idMap[folder.parent] ?: 0
                val key = localParent to folder.title
                idMap[folder.id] = localFolders[key] ?: dao.insert(
                    Bookmark(folder.title, folder.url, true, localParent, folder.order)
                ).toInt().also { localFolders[key] = it; added++ }
            }
            pending.removeAll(ready)
        }

        for (bookmark in imported.filterNot { it.isDirectory }) {
            val localParent = idMap[bookmark.parent] ?: 0
            if (localUrls.add(localParent to bookmark.url)) {
                dao.insert(Bookmark(bookmark.title, bookmark.url, false, localParent, bookmark.order))
                added++
            }
        }
        return added
    }

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

    // --- history (append rows this device doesn't have) ---

    private suspend fun exportHistoryJson(): String {
        val dtos = historyDao.getAllHistory().map { HistoryDto(it.TITLE, it.URL, it.TIME) }
        return json.encodeToString(ListSerializer(HistoryDto.serializer()), dtos)
    }

    private suspend fun importHistory(text: String): Int {
        val dtos = runCatching {
            json.decodeFromString(ListSerializer(HistoryDto.serializer()), text)
        }.getOrNull() ?: return 0
        val seen = historyDao.getAllHistory().map { it.URL to it.TIME }.toHashSet()
        var added = 0
        dtos.forEach {
            if (seen.add(it.url to it.time)) {
                historyDao.insert(HistoryRecord(it.title, it.url, it.time))
                added++
            }
        }
        return added
    }

    // --- per-site rules (translation/CSS/JS/font per host) ---

    /**
     * Android BackupUnit.restoreDatabaseData for site rules: merged field by
     * field, local wins, the backup fills whatever the local rule leaves unset
     * (or the whole rule when there is none); a rule left empty is dropped.
     * Returns the number of rules created or extended.
     */
    private suspend fun mergeDomainConfigs(imported: List<DomainConfigurationData>): Int {
        if (imported.isEmpty()) return 0
        val local = bookmarkManager.getAllDomainConfigurations()
            .associateBy({ it.domain }, { it.normalizedLegacyFlags() }).toMutableMap()
        var changed = 0
        imported.forEach { raw ->
            if (raw.domain.isBlank()) return@forEach
            val rule = raw.normalizedLegacyFlags()
            val existing = local[rule.domain]
            val merged = existing?.mergedWith(rule) ?: rule
            if (merged.isEmpty || merged == existing) return@forEach
            bookmarkManager.upsertDomainConfiguration(merged)
            local[rule.domain] = merged
            changed++
        }
        // The in-memory map is otherwise only hydrated at launch, and the user
        // may not relaunch right away.
        if (changed > 0) AppServices.config.domainConfigurationMap = local
        return changed
    }

    private fun decodeDomainConfig(text: String): DomainConfigurationData? =
        runCatching { json.decodeFromString(DomainConfigurationData.serializer(), text) }.getOrNull()

    // --- database_data.json (Android's DATABASE_DATA category) ---

    private suspend fun exportDatabaseDataJson(): String {
        // Favicon blobs are deliberately not exported (same as Android's
        // BackupUnit since its memory pass): they are re-fetched from the sites
        // and, Base64-inflated, used to dominate the backup. Restore still
        // accepts a "favicons" array from older backups.
        val articles = buildJsonArray {
            database.articleDao().getAllArticles().forEach { a ->
                add(buildJsonObject {
                    put("id", a.id); put("title", a.title); put("url", a.url)
                    put("date", a.date); put("tags", a.tags)
                })
            }
        }
        val highlights = buildJsonArray {
            database.highlightDao().getAllHighlights().forEach { h ->
                add(buildJsonObject {
                    put("id", h.id); put("articleId", h.articleId); put("content", h.content)
                })
            }
        }
        val queries = buildJsonArray {
            database.chatGptQueryDao().getAllChatGptQueriesAsync().forEach { q ->
                add(buildJsonObject {
                    put("id", q.id); put("date", q.date); put("url", q.url); put("model", q.model)
                    put("selectedText", q.selectedText); put("result", q.result)
                })
            }
        }
        val domainConfigs = buildJsonArray {
            bookmarkManager.getAllDomainConfigurations().forEach { dc ->
                add(buildJsonObject {
                    put("domain", dc.domain)
                    put("configuration", json.encodeToString(DomainConfigurationData.serializer(), dc))
                })
            }
        }
        return buildJsonObject {
            put("articles", articles)
            put("highlights", highlights)
            put("chat_gpt_queries", queries)
            put("domain_configurations", domainConfigs)
            // saved_pages / whitelist_domains / javascript_domains / cookie_domains:
            // saved pages are file-backed (see the class doc) and this app keeps
            // its domain whitelists in memory only, so there is nothing durable
            // to carry.
        }.toString()
    }

    /**
     * Android BackupUnit.restoreDatabaseData, append-only per table:
     * favicons for domains without one; articles matched on (url, date) with
     * ids remapped so highlights land on the right local article; highlights
     * deduped on (article, content); AI queries deduped on content; per-site
     * rules merged field by field. `saved_pages` and the domain lists are
     * skipped (see [exportDatabaseDataJson]).
     */
    private suspend fun importDatabaseData(text: String, summary: RestoreSummary): Boolean {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false

        root["favicons"]?.asArrayOrNull()?.let { arr ->
            val existing = database.faviconDao().getAllDomains().toHashSet()
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val domain = obj.string("domain") ?: return@forEach
                if (!existing.add(domain)) return@forEach
                val icon = obj.string("icon")?.let { runCatching { Base64.decode(it) }.getOrNull() }
                // Through the manager so its resident domain set learns the row.
                bookmarkManager.insertFavicon(FaviconInfo(domain, icon))
                summary.favicons++
            }
        }

        val articleIdMap = HashMap<Int, Int>()
        root["articles"]?.asArrayOrNull()?.let { arr ->
            val dao = database.articleDao()
            val localByKey = HashMap<Pair<String, Long>, Int>()
            dao.getAllArticles().forEach { localByKey[it.url to it.date] = it.id }
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val article = Article(
                    title = obj.string("title") ?: "",
                    url = obj.string("url") ?: return@forEach,
                    date = obj.long("date") ?: return@forEach,
                    tags = obj.string("tags") ?: "",
                )
                val importedId = obj.int("id") ?: return@forEach
                val key = article.url to article.date
                articleIdMap[importedId] = localByKey[key] ?: dao.insert(article).toInt().also {
                    localByKey[key] = it
                    summary.articles++
                }
            }
        }

        root["highlights"]?.asArrayOrNull()?.let { arr ->
            val dao = database.highlightDao()
            val seen = dao.getAllHighlights().map { it.articleId to it.content }.toHashSet()
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                // No mapped article means the article row was skipped; inserting
                // would trip the foreign key.
                val articleId = obj.int("articleId")?.let { articleIdMap[it] } ?: return@forEach
                val content = obj.string("content") ?: return@forEach
                if (seen.add(articleId to content)) {
                    dao.insert(Highlight(articleId, content))
                    summary.highlights++
                }
            }
        }

        root["chat_gpt_queries"]?.asArrayOrNull()?.let { arr ->
            val dao = database.chatGptQueryDao()
            val seen = dao.getAllChatGptQueriesAsync().map { it.mergeKey() }.toHashSet()
            val fresh = arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                ChatGptQuery(
                    date = obj.long("date") ?: return@mapNotNull null,
                    url = obj.string("url") ?: "",
                    model = obj.string("model") ?: "",
                    selectedText = obj.string("selectedText") ?: "",
                    result = obj.string("result") ?: "",
                ).takeIf { seen.add(it.mergeKey()) }
            }
            if (fresh.isNotEmpty()) dao.insertAll(fresh)
            summary.gptQueries += fresh.size
        }

        root["domain_configurations"]?.asArrayOrNull()?.let { arr ->
            val rules = arr.mapNotNull { el ->
                (el as? JsonObject)?.string("configuration")?.let(::decodeDomainConfig)
            }
            summary.siteRules += mergeDomainConfigs(rules)
        }
        return true
    }

    private fun ChatGptQuery.mergeKey() = listOf(date.toString(), url, model, selectedText)

    // --- userscripts (userscripts/<name>.user.js + userscripts/userscripts.json) ---

    private suspend fun writeUserscripts(zip: ZipWriter) {
        val manifest = mutableListOf<JsonObject>()
        val usedNames = HashSet<String>()
        AppServices.userScriptManager.getAllForBackup().forEach { (script, values) ->
            val base = script.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "script" }
            var fileName = "$base.user.js"
            var suffix = 0
            while (!usedNames.add(fileName)) fileName = "$base-${++suffix}.user.js"
            zip.addStored(USERSCRIPTS_DIR + fileName, script.code)
            manifest += buildJsonObject {
                put("file", fileName)
                put("name", script.name)
                put("enabled", script.enabled)
                script.sourceUrl?.let { put("sourceUrl", it) }
                if (values.isNotEmpty()) {
                    put("values", buildJsonObject { values.forEach { (k, v) -> put(k, v) } })
                }
            }
        }
        zip.addStored(
            USERSCRIPTS_DIR + USERSCRIPTS_META_FILE,
            buildJsonObject { put("scripts", JsonArray(manifest)) }.toString(),
        )
    }

    /** Manifest order first, then any `.user.js` the manifest doesn't know about. */
    private suspend fun importUserscripts(entries: Map<String, ByteArray>): Int {
        val bodies = entries.filterKeys { it.startsWith(USERSCRIPTS_DIR) && it.endsWith(".user.js") }
            .map { (name, bytes) -> name.removePrefix(USERSCRIPTS_DIR) to bytes.decodeToString() }
            .toMap()
        if (bodies.isEmpty()) return 0
        val metaByFile = LinkedHashMap<String, JsonObject>()
        entries[USERSCRIPTS_DIR + USERSCRIPTS_META_FILE]?.let { raw ->
            runCatching { Json.parseToJsonElement(raw.decodeToString()).jsonObject["scripts"]?.jsonArray }
                .getOrNull()?.forEach { el ->
                    val obj = el as? JsonObject ?: return@forEach
                    obj.string("file")?.takeIf { it.isNotEmpty() }?.let { metaByFile[it] = obj }
                }
        }
        val ordered = metaByFile.keys.filter { it in bodies } + bodies.keys.filter { it !in metaByFile }
        val manager = AppServices.userScriptManager
        var imported = 0
        for (file in ordered) {
            val meta = metaByFile[file]
            val values = (meta?.get("values") as? JsonObject)
                ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
                ?.toMap().orEmpty()
            val id = manager.importScript(
                code = bodies.getValue(file),
                enabled = meta?.get("enabled")?.jsonPrimitiveOrNull?.booleanOrNull ?: true,
                sourceUrl = meta?.string("sourceUrl")?.takeIf { it.isNotEmpty() },
                values = values,
            )
            if (id != null) imported++
        }
        manager.reload()
        return imported
    }

    // --- video transcripts / AI chat sessions ---

    private suspend fun exportTranscriptsJson(): String? {
        val all = database.videoTranscriptDao().getAllTranscripts()
        if (all.isEmpty()) return null
        return buildJsonArray {
            all.forEach { t ->
                add(buildJsonObject {
                    put("videoId", t.videoId); put("transcript", t.transcript); put("timestamp", t.timestamp)
                })
            }
        }.toString()
    }

    /** Local transcripts are kept (each cost a transcription); only new videos are added. */
    private suspend fun importTranscripts(text: String): Int {
        val arr = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return 0
        val dao = database.videoTranscriptDao()
        val existing = dao.getAllTranscripts().map { it.videoId }.toHashSet()
        var added = 0
        arr.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val videoId = obj.string("videoId") ?: return@forEach
            if (!existing.add(videoId)) return@forEach
            dao.insert(VideoTranscript(videoId, obj.string("transcript") ?: "", obj.long("timestamp") ?: 0L))
            added++
        }
        return added
    }

    private suspend fun exportChatSessionsJson(): String? {
        val all = database.chatSessionDao().getAllSessions()
        if (all.isEmpty()) return null
        // webContent is deliberately left out (Android does the same): it can be
        // hundreds of KB per session and is only a cache of the page text.
        return buildJsonArray {
            all.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id); put("title", s.title); put("created", s.created)
                    put("lastUpdated", s.lastUpdated); put("webTitle", s.webTitle)
                    put("webUrl", s.webUrl); put("messages", s.messages)
                })
            }
        }.toString()
    }

    /** Sessions this device lacks are appended; a session held on both sides
     *  (same UUID) is only touched when the backup copy is strictly newer, and
     *  then keeps the local page text the backup doesn't carry. */
    private suspend fun importChatSessions(text: String): Int {
        val arr = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return 0
        val dao = database.chatSessionDao()
        val existing = dao.getAllSessions().associateBy { it.id }
        val fresh = arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            val lastUpdated = obj.long("lastUpdated") ?: 0L
            val local = existing[id]
            if (local != null && local.lastUpdated >= lastUpdated) return@mapNotNull null
            ChatSession(
                id = id,
                title = obj.string("title") ?: "",
                created = obj.long("created") ?: 0L,
                lastUpdated = lastUpdated,
                webTitle = obj.string("webTitle") ?: "",
                webUrl = obj.string("webUrl") ?: "",
                messages = obj.string("messages") ?: "[]",
                webContent = local?.webContent ?: "",
            )
        }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        return fresh.size
    }

    // --- full backup zip ---

    suspend fun exportBackupZip(): ByteArray {
        val zip = ZipWriter()
        val categories = mutableListOf("GPT_SETTINGS", "BOOKMARKS", "HISTORY", "DATABASE_DATA", "USERSCRIPTS")
        val prefsJson = portablePrefsOnly(prefs.exportPrefs())
        zip.addStored("prefs.json", prefsJson)
        zip.addStored(GPT_SETTINGS_FILE, gptSettingsOnly(prefsJson))
        zip.addStored(BOOKMARKS_FILE, exportBookmarksJson())
        zip.addStored(HISTORY_FILE, exportHistoryJson())
        zip.addStored(DATABASE_DATA_FILE, exportDatabaseDataJson())
        writeUserscripts(zip)
        exportTranscriptsJson()?.let { zip.addStored(TRANSCRIPTS_FILE, it); categories += "TRANSCRIPTS" }
        exportChatSessionsJson()?.let { zip.addStored(CHAT_SESSIONS_FILE, it); categories += "CHAT_SESSIONS" }
        zip.addStored(
            MANIFEST_FILE,
            buildJsonObject {
                put("version", 2)
                put("app", "einkbro-ios")
                put("categories", JsonArray(categories.map { JsonPrimitive(it) }))
            }.toString(),
        )
        return zip.build()
    }

    /**
     * Appends everything in a backup zip (this app's, or Android's BackupUnit v2
     * — the file the Android app syncs to Google Drive) onto the local data;
     * see the class doc for the per-table rules. Returns null when the zip
     * holds nothing EinkBro recognises.
     */
    suspend fun importBackupZip(
        bytes: ByteArray,
        categories: Set<BackupCategory> = BackupCategory.entries.toSet(),
    ): RestoreSummary? {
        val entries = ZipReader.read(bytes) ?: return null
        val s = RestoreSummary()
        fun found() { s.recognized = true }
        fun wanted(c: BackupCategory) = c in categories

        // Preferences. Android's ALL_PREFERENCES supersedes its GPT_SETTINGS
        // subset (same keys); both go through the same fill-missing-only path.
        if (wanted(BackupCategory.ALL_PREFERENCES)) {
            entries["prefs.json"]?.let { s.prefs += importPrefsJson(it.decodeToString()); found() }
            entries.filterKeys { it.startsWith("shared_prefs/") && it.endsWith(".xml") }
                .forEach { (_, xml) -> s.prefs += importAndroidPrefsXml(xml.decodeToString()); found() }
        }
        if (wanted(BackupCategory.GPT_SETTINGS) && !wanted(BackupCategory.ALL_PREFERENCES)) {
            entries[GPT_SETTINGS_FILE]?.let { s.prefs += importPrefsJson(it.decodeToString()); found() }
        }

        if (wanted(BackupCategory.BOOKMARKS)) {
            entries[BOOKMARKS_FILE]?.let { s.bookmarks += importBookmarks(it.decodeToString()); found() }
        }
        if (wanted(BackupCategory.HISTORY)) {
            entries[HISTORY_FILE]?.let { s.history += importHistory(it.decodeToString()); found() }
        }
        if (wanted(BackupCategory.DATABASE_DATA)) {
            // Older iOS backups carried the per-site rules as their own entry.
            entries["domain_configs.json"]?.let {
                val rules = runCatching {
                    json.decodeFromString(ListSerializer(DomainConfigurationData.serializer()), it.decodeToString())
                }.getOrDefault(emptyList())
                s.siteRules += mergeDomainConfigs(rules); found()
            }
            entries[DATABASE_DATA_FILE]?.let { if (importDatabaseData(it.decodeToString(), s)) found() }
        }
        if (wanted(BackupCategory.USERSCRIPTS)) {
            importUserscripts(entries).let {
                if (it > 0 || entries.keys.any { k -> k.startsWith(USERSCRIPTS_DIR) }) { s.userscripts += it; found() }
            }
        }
        if (wanted(BackupCategory.TRANSCRIPTS)) {
            entries[TRANSCRIPTS_FILE]?.let { s.transcripts += importTranscripts(it.decodeToString()); found() }
        }
        if (wanted(BackupCategory.CHAT_SESSIONS)) {
            entries[CHAT_SESSIONS_FILE]?.let { s.chatSessions += importChatSessions(it.decodeToString()); found() }
        }

        // Config delegates read the prefs store live, so restored settings apply on
        // next read; a relaunch guarantees everything (incl. cached sub-configs).
        // Theme prefs are mirrored into Compose state, so retint now.
        if (s.prefs > 0) info.plateaukao.einkbro.view.compose.UiThemeState.syncFrom(AppServices.config.display)
        return s.takeIf { it.recognized }
    }

    /**
     * Android BackupUnit.getAvailableCategoryOptions: the categories a zip holds,
     * each with the raw byte size of its entries, in enum order. Derived from
     * the entries themselves (not the manifest) so older iOS zips without
     * `_manifest.json` still restore. Null when nothing is recognised.
     */
    fun scanCategories(bytes: ByteArray): List<Pair<BackupCategory, Long>>? {
        val entries = ZipReader.read(bytes) ?: return null
        val sizes = LinkedHashMap<BackupCategory, Long>()
        entries.forEach { (name, data) ->
            categoryForEntry(name)?.let { sizes[it] = (sizes[it] ?: 0L) + data.size }
        }
        if (sizes.isEmpty()) return null
        return BackupCategory.entries.filter { it in sizes }.map { it to sizes.getValue(it) }
    }

    private fun categoryForEntry(name: String): BackupCategory? = when {
        name == "prefs.json" || name.startsWith("shared_prefs/") -> BackupCategory.ALL_PREFERENCES
        name == GPT_SETTINGS_FILE -> BackupCategory.GPT_SETTINGS
        name == BOOKMARKS_FILE -> BackupCategory.BOOKMARKS
        name == HISTORY_FILE -> BackupCategory.HISTORY
        name == DATABASE_DATA_FILE || name == "domain_configs.json" -> BackupCategory.DATABASE_DATA
        name.startsWith(USERSCRIPTS_DIR) -> BackupCategory.USERSCRIPTS
        name == TRANSCRIPTS_FILE -> BackupCategory.TRANSCRIPTS
        name == CHAT_SESSIONS_FILE -> BackupCategory.CHAT_SESSIONS
        else -> null
    }

    /** android.text.format.Formatter.formatShortFileSize stand-in. */
    fun formatShortFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> {
            val mb = bytes / (1024.0 * 1024.0)
            val tenths = (mb * 10 + 0.5).toLong()
            "${tenths / 10}.${tenths % 10} MB"
        }
    }

    // --- preferences ---

    // Keys that must never cross devices:
    //  - the Drive OAuth session is the *other* device's login (Android keeps
    //    it under sp_; this app's own token key has no sp_ prefix on purpose);
    //  - the e-ink image prefs are Android panel tuning iOS has no display or
    //    UI for, so importing them would silently distort image colors;
    //  - the saved-file lists (read-later EPUBs/PDFs) are references to files
    //    that never leave the device that wrote them — a backup carries no file
    //    bytes, so restoring the list only yields entries nothing can open.
    private val NON_PORTABLE_KEYS = setOf(
        "sp_drive_auth_state", "sp_drive_pending_auth",
        "sp_image_adjustment", "sp_eink_image_mode",
        info.plateaukao.einkbro.preference.ConfigManager.K_SAVED_EPUBS,
        info.plateaukao.einkbro.preference.ConfigManager.K_SAVED_PDFS,
    )

    private fun isPrivateKey(key: String) = key in NON_PORTABLE_KEYS

    /** Drops [NON_PORTABLE_KEYS] from a `prefs.json` snapshot, in both directions. */
    private fun portablePrefsOnly(prefsJson: String): String {
        val obj = runCatching { Json.parseToJsonElement(prefsJson).jsonObject }.getOrNull()
            ?: return prefsJson
        return JsonObject(obj.filterKeys { !isPrivateKey(it) }).toString()
    }

    /** The Gen-AI subset of a prefs snapshot (Android's gpt_settings.json). */
    private fun gptSettingsOnly(prefsJson: String): String {
        val obj = runCatching { Json.parseToJsonElement(prefsJson).jsonObject }.getOrNull()
            ?: return "{}"
        return JsonObject(obj.filterKeys { it in GPT_PREF_KEYS }).toString()
    }

    /**
     * Append-only prefs import for a flat `{key: value}` JSON object (our
     * prefs.json or Android's gpt_settings.json): a key this device has
     * already written keeps its local value; only never-set keys are filled.
     * Returns the number of keys added.
     */
    private fun importPrefsJson(text: String): Int {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return 0
        val fresh = obj.filterKeys { !isPrivateKey(it) && !prefs.contains(it) }
            .filterValues { it !is JsonNull }
        if (fresh.isEmpty()) return 0
        prefs.importPrefs(JsonObject(fresh).toString())
        return fresh.size
    }

    /** Android SharedPreferences XML (`<map><boolean name=… value=…/>…</map>`), append-only. */
    private fun importAndroidPrefsXml(xml: String): Int {
        var imported = 0
        fun accepts(key: String) = !isPrivateKey(key) && !prefs.contains(key)
        Regex("""<(boolean|int|long|float)\s+name="([^"]*)"\s+value="([^"]*)"\s*/>""")
            .findAll(xml).forEach { m ->
                val (type, key, value) = m.destructured
                if (!accepts(key)) return@forEach
                runCatching {
                    when (type) {
                        "boolean" -> prefs.edit().putBoolean(key, value.toBooleanStrict()).apply()
                        "int" -> prefs.edit().putInt(key, value.toInt()).apply()
                        "long" -> prefs.edit().putLong(key, value.toLong()).apply()
                        else -> prefs.edit().putFloat(key, value.toFloat()).apply()
                    }
                    imported++
                }
            }
        Regex("""<string\s+name="([^"]*)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { m ->
                val key = m.groupValues[1]
                if (!accepts(key)) return@forEach
                prefs.edit().putString(key, unescapeXml(m.groupValues[2])).apply()
                imported++
            }
        Regex("""<set\s+name="([^"]*)">(.*?)</set>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).forEach { m ->
                val key = m.groupValues[1]
                if (!accepts(key)) return@forEach
                val values = Regex("<string>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(m.groupValues[2]).map { unescapeXml(it.groupValues[1]) }.toSet()
                prefs.edit().putStringSet(key, values).apply()
                imported++
            }
        return imported
    }

    private fun unescapeXml(s: String): String = s
        .replace(Regex("""&#(\d+);""")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    // --- small JSON accessors (Android's org.json optString/optLong style) ---

    private val JsonElement.jsonPrimitiveOrNull: JsonPrimitive? get() = this as? JsonPrimitive
    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitiveOrNull?.contentOrNull
    private fun JsonObject.long(key: String): Long? = get(key)?.jsonPrimitiveOrNull?.longOrNull
    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitiveOrNull?.intOrNull

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

    // Entry names shared with Android's BackupUnit.
    private const val MANIFEST_FILE = "_manifest.json"
    private const val GPT_SETTINGS_FILE = "gpt_settings.json"
    private const val BOOKMARKS_FILE = "bookmarks.json"
    private const val HISTORY_FILE = "history.json"
    private const val DATABASE_DATA_FILE = "database_data.json"
    private const val USERSCRIPTS_DIR = "userscripts/"
    private const val USERSCRIPTS_META_FILE = "userscripts.json"
    private const val TRANSCRIPTS_FILE = "transcripts.json"
    private const val CHAT_SESSIONS_FILE = "chat_sessions.json"

    /** Android BackupUnit.GPT_PREF_KEYS — the Gen-AI settings subset. */
    private val GPT_PREF_KEYS = setOf(
        "sp_gpt_api_key", "sp_gemini_api_key", "sp_gpt_system_prompt", "sp_gpt_user_prompt",
        "sp_gpt_user_prompt_web_page", "sp_gp_model", "sp_gpt_voice_model", "sp_gpt_voice_prompt",
        "sp_alternative_model", "sp_gemini_model", "sp_use_openai_tts", "sp_external_search_with_gpt",
        "sp_enable_open_ai_stream", "sp_gpt_action_items", "sp_gpt_action_external",
        "sp_gpt_for_chat_web", "sp_gpt_for_summary", "sp_gpt_server_url", "sp_use_custom_gpt_url",
        "sp_use_gemini_api", "K_GPT_VOICE_OPTION",
    )
}
