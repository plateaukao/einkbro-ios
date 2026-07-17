package info.plateaukao.einkbro.backup

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.database.HistoryRecord
import info.plateaukao.einkbro.epub.ZipReader
import info.plateaukao.einkbro.epub.ZipWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
        return zip.build()
    }

    /** Restores prefs + bookmarks + history from a backup zip; returns false if unreadable. */
    suspend fun importBackupZip(bytes: ByteArray): Boolean {
        val entries = ZipReader.read(bytes) ?: return false
        entries["prefs.json"]?.let { AppServices.sharedPreferences.importPrefs(it.decodeToString()) }
        entries["bookmarks.json"]?.let { importBookmarks(it.decodeToString()) }
        entries["history.json"]?.let { importHistory(it.decodeToString()) }
        // Config delegates read the prefs store live, so restored settings apply on
        // next read; a relaunch guarantees everything (incl. cached sub-configs).
        return true
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
