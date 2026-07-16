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
    // (viewModelScope is Main.immediate), so this must already be initialized.
    private var sortMode = SortMode.BY_ORDER

    init {
        viewModelScope.launch {
            bookmarkManager.seedDefaultsIfEmpty()
            refresh()
        }
    }

    private fun updateUiState() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val bookmarks = bookmarkManager.getBookmarksByParent(folderStack.last().id)
        currentFolder.value = folderStack.last()
        _uiState.value = if (sortMode == SortMode.BY_ORDER) {
            bookmarks.sortedBy { it.order }
        } else {
            bookmarks.sortedBy { it.title }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkManager.delete(bookmark)
            refresh()
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
            bookmarkManager.insert(bookmark)
            refresh()
            doneAction?.invoke()
        }
    }

    fun updateBookmarksOrder(bookmarks: List<Bookmark>) {
        viewModelScope.launch {
            bookmarks.forEachIndexed { index, bookmark ->
                bookmark.order = index
                bookmarkManager.update(bookmark)
            }
            sortMode = SortMode.BY_ORDER
            refresh()
        }
    }

    suspend fun insertDirectory(title: String, parentId: Int = 0) {
        bookmarkManager.insert(
            Bookmark(
                title = title,
                url = "",
                isDirectory = true,
                parent = parentId,
            )
        )
        refresh()
    }

    suspend fun getBookmarkFolders(): List<Bookmark> = bookmarkManager.getBookmarkFolders()

    private enum class SortMode { BY_ORDER, BY_TITLE }
}
