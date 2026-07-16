package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.SavedPage
import info.plateaukao.einkbro.util.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Room-backed (via BookmarkManager) list of offline saved pages. */
class SavedPageViewModel : ViewModel() {

    private val manager = AppServices.bookmarkManager
    private val savedPages = MutableStateFlow<List<SavedPage>>(emptyList())

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch { savedPages.value = manager.getAllSavedPages() }
    }

    fun getAllSavedPages(): Flow<List<SavedPage>> = savedPages

    fun deleteSavedPage(savedPage: SavedPage) {
        viewModelScope.launch {
            FileStore.delete(savedPage.filePath)
            manager.deleteSavedPage(savedPage)
            refresh()
        }
    }
}
