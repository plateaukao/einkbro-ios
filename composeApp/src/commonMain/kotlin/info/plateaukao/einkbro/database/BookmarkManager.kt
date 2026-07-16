package info.plateaukao.einkbro.database

/**
 * In-memory stand-in for the Room-backed BookmarkManager, preloaded with
 * sample data so bookmark UIs have something to show.
 */
class BookmarkManager {
    val bookmarks = mutableListOf(
        Bookmark(title = "EinkBro", url = "https://github.com/plateaukao/einkbro"),
        Bookmark(title = "Wikipedia", url = "https://wikipedia.org"),
        Bookmark(title = "Hacker News", url = "https://news.ycombinator.com"),
    )

    private val domainConfigurations = mutableListOf<DomainConfigurationData>()

    fun addDomainConfiguration(data: DomainConfigurationData) {
        domainConfigurations.removeAll { it.domain == data.domain }
        domainConfigurations.add(data)
    }

    suspend fun getAllBookmarks(): List<Bookmark> = bookmarks.toList()

    suspend fun getBookmarksByParent(parent: Int): List<Bookmark> =
        bookmarks.filter { it.parent == parent }

    suspend fun insert(bookmark: Bookmark) { bookmarks.add(bookmark) }

    suspend fun delete(bookmark: Bookmark) { bookmarks.remove(bookmark) }

    suspend fun existsUrl(url: String): Boolean = bookmarks.any { it.url == url }

    // Favicons are not persisted in the iOS UI catalog; callers fall back to a
    // default globe icon when this returns null.
    fun findFaviconBitmapBy(url: String): androidx.compose.ui.graphics.ImageBitmap? = null
}
