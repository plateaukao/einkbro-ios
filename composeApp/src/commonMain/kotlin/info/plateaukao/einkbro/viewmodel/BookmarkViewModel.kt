package info.plateaukao.einkbro.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.database.BookmarkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BookmarkViewModel(private val bookmarkManager: BookmarkManager) : ViewModel() {

    private val _uiState = MutableStateFlow<List<Bookmark>>(emptyList())
    val uiState: StateFlow<List<Bookmark>> = _uiState

    // java.util.Stack replaced with a plain MutableList used LIFO.
    private val folderStack: MutableList<Bookmark> = mutableListOf(Bookmark("", ""))

    var currentFolder: MutableState<Bookmark> = mutableStateOf(folderStack.last())

    // Declared before init: updateUiState() runs synchronously inside init
    // (viewModelScope is Main.immediate and nothing suspends before the read),
    // so this must already be initialized there or Kotlin/Native crashes.
    private var sortMode = SortMode.BY_ORDER

    init {
        seedSampleData()
        updateUiState()
    }

    private fun updateUiState() {
        viewModelScope.launch {
            val bookmarks = bookmarkManager.getBookmarksByParent(folderStack.last().id)
            currentFolder.value = folderStack.last()
            if (sortMode == SortMode.BY_ORDER) {
                _uiState.value = bookmarks.sortedBy { bookmark -> bookmark.order }
            } else {
                _uiState.value = bookmarks.sortedBy { bookmark -> bookmark.title }
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkManager.delete(bookmark)
            updateUiState()
        }
    }

    fun getFavicon(bookmark: Bookmark): ImageBitmap? =
        bookmarkManager.findFaviconBitmapBy(bookmark.url)

    fun toRootFolder() {
        while (folderStack.size > 1) {
            folderStack.removeAt(folderStack.lastIndex)
        }
        updateUiState()
    }

    fun outOfFolder() {
        if (folderStack.size > 1) {
            folderStack.removeAt(folderStack.lastIndex)
            updateUiState()
        }
    }

    fun intoFolder(bookmark: Bookmark) {
        folderStack.add(bookmark)
        updateUiState()
    }

    fun insertBookmark(bookmark: Bookmark, doneAction: (() -> Unit)? = null) {
        viewModelScope.launch {
            insertWithId(bookmark)
            updateUiState()
            doneAction?.invoke()
        }
    }

    fun updateBookmarksOrder(bookmarks: List<Bookmark>) {
        viewModelScope.launch {
            // The shared BookmarkManager stub has no updateBookmarksOrder();
            // Bookmark.order is mutable and the stub returns the same
            // instances, so persisting the order in-place is equivalent.
            bookmarks.forEachIndexed { index, bookmark -> bookmark.order = index }
            sortMode = SortMode.BY_ORDER
            updateUiState()
        }
    }

    suspend fun insertDirectory(title: String, parentId: Int = 0) {
        insertWithId(
            Bookmark(
                title = title,
                url = "",
                isDirectory = true,
                parent = parentId,
            )
        )
        updateUiState()
    }

    suspend fun getBookmarkFolders(): List<Bookmark> =
        // The shared BookmarkManager stub has no getBookmarkFolders(); derive it.
        bookmarkManager.getAllBookmarks().filter { it.isDirectory }

    /**
     * The BookmarkManager stub appends with whatever id the entity carries
     * (no Room auto-generation / REPLACE semantics), but BookmarkList uses id
     * as the LazyGrid key, which must be unique. Mimic Room: id == 0 gets the
     * next free id; a matching existing id is an update (replace the row).
     */
    private suspend fun insertWithId(bookmark: Bookmark) {
        val existing = bookmarkManager.bookmarks
        if (bookmark.id == 0) {
            bookmark.id = (existing.maxOfOrNull { it.id } ?: 0) + 1
        } else {
            existing.firstOrNull { it !== bookmark && it.id == bookmark.id }?.let {
                bookmark.order = it.order
                bookmarkManager.delete(it)
            }
        }
        if (existing.none { it === bookmark }) {
            bookmarkManager.insert(bookmark)
        }
    }

    /**
     * Give the stub's pre-loaded bookmarks unique ids and add sample folders
     * (with nested entries) so the folder UI has something to show.
     * Idempotent: runs only while the stub still has no directories.
     */
    private fun seedSampleData() {
        val bookmarks = bookmarkManager.bookmarks
        if (bookmarks.any { it.isDirectory }) return

        bookmarks.forEachIndexed { index, bookmark ->
            if (bookmark.id == 0) bookmark.id = index + 1
            bookmark.order = index
        }

        var nextId = (bookmarks.maxOfOrNull { it.id } ?: 0) + 1
        val newsFolder = Bookmark(title = "News", url = "", isDirectory = true)
            .apply { id = nextId++; order = bookmarks.size }
        val devFolder = Bookmark(title = "Dev", url = "", isDirectory = true)
            .apply { id = nextId++; order = bookmarks.size + 1 }
        val children = listOf(
            Bookmark(title = "BBC", url = "https://bbc.com", parent = newsFolder.id)
                .apply { id = nextId++; order = 0 },
            Bookmark(title = "NHK", url = "https://nhk.or.jp", parent = newsFolder.id)
                .apply { id = nextId++; order = 1 },
            Bookmark(title = "Kotlin", url = "https://kotlinlang.org", parent = devFolder.id)
                .apply { id = nextId++; order = 0 },
        )
        bookmarks.add(newsFolder)
        bookmarks.add(devFolder)
        bookmarks.addAll(children)
    }

    private enum class SortMode { BY_ORDER, BY_TITLE }
}
