package info.plateaukao.einkbro.database

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val chatSessionDao = database.chatSessionDao()

    // For the fire-and-forget calls that come from non-suspend contexts
    // (ConfigManager property setters).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    // Only the favicon domain names stay resident (a few KB); the icon blobs
    // used to be loaded in full at startup and held for the process lifetime,
    // 10-40 MB on a mature install, and answered lookups by linear scan. A
    // bitmap is now loaded per host on demand into a bounded cache that is
    // snapshot state, so a composable reading it recomposes when the decode
    // lands (Android did the same in its memory pass).
    private val faviconDomains = HashSet<String>()
    // Snapshot state so a composable that asked before the domain set landed
    // (cold start) recomposes and asks again once it has.
    private val faviconDomainsReady = mutableStateOf(false)
    private val faviconBitmapCache = mutableStateMapOf<String, ImageBitmap>()
    private val faviconCacheOrder = ArrayDeque<String>()
    private val faviconLoadsInFlight = HashSet<String>()

    init {
        ioScope.launch {
            val domains = faviconDao.getAllDomains()
            withContext(Dispatchers.Main) {
                faviconDomains.addAll(domains)
                faviconDomainsReady.value = true
            }
        }
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

    fun deleteDomainConfiguration(key: String) {
        ioScope.launch { domainConfigurationDao.deleteByDomain(key) }
    }

    /** Same row write as [addDomainConfiguration], but awaited — restore needs
     *  the rows in place before it re-reads them to refresh the in-memory map. */
    suspend fun upsertDomainConfiguration(data: DomainConfigurationData) {
        domainConfigurationDao.insert(
            DomainConfiguration(
                domain = data.domain,
                configuration = json.encodeToString(DomainConfigurationData.serializer(), data),
            )
        )
    }

    suspend fun getAllDomainConfigurations(): List<DomainConfigurationData> =
        domainConfigurationDao.getAll().mapNotNull {
            runCatching {
                json.decodeFromString(DomainConfigurationData.serializer(), it.configuration)
                    .normalizedLegacyFlags()
            }.getOrNull()
        }

    suspend fun findFaviconBy(url: String): FaviconInfo? {
        val host = info.plateaukao.einkbro.util.Uri.parse(url).host ?: return null
        return faviconDao.findBy(host)
    }

    suspend fun insertFavicon(faviconInfo: FaviconInfo) {
        faviconDao.insert(faviconInfo)
        withContext(Dispatchers.Main) {
            faviconDomains.add(faviconInfo.domain)
            // Drop the stale decode; the next read reloads from the new blob.
            faviconBitmapCache.remove(faviconInfo.domain)
            faviconCacheOrder.remove(faviconInfo.domain)
        }
    }

    /** True once [host] has a stored icon (main thread; no I/O). */
    fun hasFavicon(host: String): Boolean = host in faviconDomains

    /**
     * Decodes [host]'s stored icon off the main thread (or serves the cached
     * bitmap) and hands it to [onLoaded] on the main thread; null when the host
     * has no usable icon.
     */
    fun loadFaviconBitmap(host: String, onLoaded: (ImageBitmap?) -> Unit) {
        faviconBitmapCache[host]?.let { onLoaded(it); return }
        ioScope.launch {
            val bitmap = faviconDao.findBy(host)?.getBitmap()
            withContext(Dispatchers.Main) {
                if (bitmap != null) cacheFaviconBitmap(host, bitmap)
                onLoaded(bitmap)
            }
        }
    }

    /** Memory-pressure hook (Android onTrimMemory): drop the decoded bitmaps. */
    fun trimMemory() {
        faviconBitmapCache.clear()
        faviconCacheOrder.clear()
    }

    private fun cacheFaviconBitmap(host: String, bitmap: ImageBitmap) {
        if (host !in faviconBitmapCache && faviconCacheOrder.size >= FAVICON_BITMAP_CACHE_SIZE) {
            faviconCacheOrder.removeFirstOrNull()?.let { faviconBitmapCache.remove(it) }
        }
        if (host !in faviconBitmapCache) faviconCacheOrder.addLast(host)
        faviconBitmapCache[host] = bitmap
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

    // Chat-with-web sessions (Android BookmarkManager's chat_sessions facade;
    // chat.html reads/writes these through ChatWebInterface).
    suspend fun getAllChatSessions(): List<ChatSession> = chatSessionDao.getAllSessions()

    suspend fun getChatSessionById(sessionId: String): ChatSession? =
        chatSessionDao.getSessionById(sessionId)

    suspend fun upsertChatSession(session: ChatSession) = chatSessionDao.upsert(session)

    suspend fun deleteChatSession(sessionId: String) = chatSessionDao.deleteById(sessionId)

    suspend fun deleteAllChatSessions() = chatSessionDao.deleteAll()

    // --- cached video transcripts (YouTube caption fallback) ---

    suspend fun getVideoTranscript(videoId: String): VideoTranscript? =
        database.videoTranscriptDao().getTranscript(videoId)

    suspend fun insertVideoTranscript(videoTranscript: VideoTranscript) =
        database.videoTranscriptDao().insert(videoTranscript)

    /**
     * Favicon bitmap for [url] from composition: returns the cached decode, or
     * null while one is loading. Reads snapshot state, so call it directly in
     * the composable (not inside `remember`) and the item recomposes with the
     * bitmap once the decode lands. Unknown hosts answer null with no I/O.
     * Decoded bitmaps are cached per domain so repeated reads return the same
     * instance; Compose skipping relies on that.
     */
    fun findFaviconBitmapBy(url: String): ImageBitmap? {
        val host = info.plateaukao.einkbro.util.Uri.parse(url).host ?: return null
        faviconBitmapCache[host]?.let { return it }
        if (!faviconDomainsReady.value) return null
        if (host !in faviconDomains || !faviconLoadsInFlight.add(host)) return null
        ioScope.launch {
            val bitmap = faviconDao.findBy(host)?.getBitmap()
            withContext(Dispatchers.Main) {
                faviconLoadsInFlight.remove(host)
                if (bitmap != null) cacheFaviconBitmap(host, bitmap)
            }
        }
        return null
    }

    private companion object {
        const val FAVICON_BITMAP_CACHE_SIZE = 200
    }
}
