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

    // For the fire-and-forget calls that come from non-suspend contexts
    // (ConfigManager property setters).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    suspend fun insertFavicon(faviconInfo: FaviconInfo) = faviconDao.insert(faviconInfo)

    // Favicon bitmaps are not rendered yet (decode helper arrives with the
    // favicon-capture work); UI falls back to the default globe icon.
    fun findFaviconBitmapBy(url: String): androidx.compose.ui.graphics.ImageBitmap? = null
}
