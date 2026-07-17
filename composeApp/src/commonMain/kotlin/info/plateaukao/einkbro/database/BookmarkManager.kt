package info.plateaukao.einkbro.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Room-backed bookmark/domain-config facade (was an in-memory stub before
 * Phase 2). API kept compatible with the ported UI call sites.
 */
class BookmarkManager(private val database: AppDatabase) {

    private val bookmarkDao = database.bookmarkDao()
    private val faviconDao = database.faviconDao()
    private val domainConfigurationDao = database.domainConfigurationDao()
    private val articleDao = database.articleDao()
    private val highlightDao = database.highlightDao()
    private val savedPageDao = database.savedPageDao()
    private val chatGptQueryDao = database.chatGptQueryDao()

    // For the fire-and-forget calls that come from non-suspend contexts
    // (ConfigManager property setters).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Mirrors Android BookmarkManager: favicons are kept in memory so lookups
    // from composition (remember { getFavicon(...) }) stay synchronous.
    private val faviconInfos: MutableList<FaviconInfo> = mutableListOf()
    private val faviconBitmapCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    init {
        ioScope.launch { faviconInfos.addAll(faviconDao.getAllFavicons()) }
    }

    suspend fun getAllBookmarks(): List<Bookmark> = bookmarkDao.getAllBookmarks()

    suspend fun getBookmarksByParent(parent: Int): List<Bookmark> =
        bookmarkDao.getBookmarksByParent(parent)

    suspend fun getBookmarkFolders(): List<Bookmark> = bookmarkDao.getBookmarkFolders()

    suspend fun insert(bookmark: Bookmark) {
        bookmarkDao.insert(bookmark)
    }

    suspend fun update(bookmark: Bookmark) {
        bookmarkDao.update(bookmark)
    }

    suspend fun delete(bookmark: Bookmark) {
        bookmarkDao.delete(bookmark)
    }

    suspend fun existsUrl(url: String): Boolean = bookmarkDao.getBookmarkByUrl(url) != null

    /** Replaces all bookmarks (parity Phase J backup/import). */
    suspend fun overwriteBookmarks(bookmarks: List<Bookmark>) {
        bookmarkDao.deleteAll()
        bookmarks.forEach { bookmarkDao.insert(it) }
    }

    /** First-launch convenience so the bookmark UI isn't empty. */
    suspend fun seedDefaultsIfEmpty() {
        if (bookmarkDao.count() > 0) return
        bookmarkDao.insert(Bookmark(title = "EinkBro", url = "https://github.com/plateaukao/einkbro"))
        bookmarkDao.insert(Bookmark(title = "Wikipedia", url = "https://wikipedia.org"))
        bookmarkDao.insert(Bookmark(title = "Hacker News", url = "https://news.ycombinator.com"))
    }

    fun addDomainConfiguration(data: DomainConfigurationData) {
        ioScope.launch {
            domainConfigurationDao.insert(
                DomainConfiguration(
                    domain = data.domain,
                    configuration = json.encodeToString(DomainConfigurationData.serializer(), data),
                )
            )
        }
    }

    suspend fun getAllDomainConfigurations(): List<DomainConfigurationData> =
        domainConfigurationDao.getAll().mapNotNull {
            runCatching {
                json.decodeFromString(DomainConfigurationData.serializer(), it.configuration)
            }.getOrNull()
        }

    suspend fun findFaviconBy(url: String): FaviconInfo? {
        val host = info.plateaukao.einkbro.util.Uri.parse(url).host ?: return null
        return faviconDao.findBy(host)
    }

    suspend fun insertFavicon(faviconInfo: FaviconInfo) {
        faviconDao.insert(faviconInfo)
        faviconInfos.removeAll { it.domain == faviconInfo.domain }
        faviconInfos.add(faviconInfo)
        faviconBitmapCache.remove(faviconInfo.domain)
    }

    /** Fire-and-forget variant for non-suspend callers (the web engine). */
    fun insertFaviconAsync(faviconInfo: FaviconInfo) {
        ioScope.launch { insertFavicon(faviconInfo) }
    }

    // --- highlights (Phase 5) ---

    suspend fun getAllArticles(): List<Article> = articleDao.getAllArticles()

    suspend fun getArticle(articleId: Int): Article? = articleDao.getArticleById(articleId)

    suspend fun getHighlightsForArticle(articleId: Int): List<Highlight> =
        highlightDao.getHighlightsForArticle(articleId)

    suspend fun deleteArticle(articleId: Int) = articleDao.deleteById(articleId)

    suspend fun deleteHighlight(highlight: Highlight) = highlightDao.delete(highlight)

    /**
     * Saves a highlight: upserts one [Article] per URL, then attaches the
     * selected text as a [Highlight] (mirrors Android's ActionModeDelegate).
     */
    suspend fun saveHighlight(url: String, title: String, content: String) {
        val articleId = (articleDao.getArticleByUrl(url)
            ?: run {
                val id = articleDao.insert(
                    Article(
                        title = title.ifBlank { url },
                        url = url,
                        date = info.plateaukao.einkbro.util.System.currentTimeMillis(),
                        tags = "",
                    )
                )
                articleDao.getArticleByUrl(url) ?: Article(title, url, 0, "").apply { this.id = id.toInt() }
            }).id
        highlightDao.insert(Highlight(articleId = articleId, content = content))
    }

    // --- saved pages (Phase 7) ---

    suspend fun getAllSavedPages(): List<SavedPage> = savedPageDao.getAll()

    suspend fun insertSavedPage(savedPage: SavedPage): SavedPage {
        val id = savedPageDao.insert(savedPage)
        return savedPage.apply { this.id = id.toInt() }
    }

    suspend fun deleteSavedPage(savedPage: SavedPage) = savedPageDao.delete(savedPage)

    // --- persisted AI queries (Phase K) ---

    fun getAllChatGptQueries(): kotlinx.coroutines.flow.Flow<List<ChatGptQuery>> =
        chatGptQueryDao.getAllChatGptQueries()

    suspend fun getAllChatGptQueriesAsync(): List<ChatGptQuery> =
        chatGptQueryDao.getAllChatGptQueriesAsync()

    suspend fun addChatGptQuery(chatGptQuery: ChatGptQuery) =
        chatGptQueryDao.addChatGptQuery(chatGptQuery)

    suspend fun deleteChatGptQuery(chatGptQuery: ChatGptQuery) =
        chatGptQueryDao.deleteChatGptQuery(chatGptQuery)

    suspend fun deleteAllChatGptQueries() = chatGptQueryDao.deleteAll()

    // Decoded bitmaps are cached per domain so repeated lookups return the same
    // instance; Compose skipping and mutableStateOf equality rely on that.
    fun findFaviconBitmapBy(url: String): androidx.compose.ui.graphics.ImageBitmap? {
        val host = info.plateaukao.einkbro.util.Uri.parse(url).host ?: return null
        faviconBitmapCache[host]?.let { return it }
        val bitmap = faviconInfos.firstOrNull { it.domain == host }?.getBitmap() ?: return null
        if (faviconBitmapCache.size >= 100) {
            faviconBitmapCache.remove(faviconBitmapCache.keys.first())
        }
        faviconBitmapCache[host] = bitmap
        return bitmap
    }
}
