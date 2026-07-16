package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.database.SavedPage
import info.plateaukao.einkbro.util.System
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * In-memory port of the Android SavedPageViewModel (Room + files there);
 * preloaded with sample saved pages for the catalog.
 */
class SavedPageViewModel : ViewModel() {

    private val savedPages = MutableStateFlow(
        listOf(
            SavedPage(
                title = "E Ink - Wikipedia",
                url = "https://en.wikipedia.org/wiki/E_Ink",
                filePath = "/data/saved_pages/e_ink_wikipedia.mht",
                savedAt = System.currentTimeMillis() - 2L * 86_400_000L,
            ).apply { id = 1 },
            SavedPage(
                title = "EinkBro - A Browser for E Ink devices",
                url = "https://github.com/plateaukao/einkbro",
                filePath = "/data/saved_pages/einkbro_readme.mht",
                savedAt = System.currentTimeMillis() - 86_400_000L,
            ).apply { id = 2 },
            SavedPage(
                title = "Compose Multiplatform documentation",
                url = "https://www.jetbrains.com/compose-multiplatform/",
                filePath = "/data/saved_pages/cmp_docs.mht",
                savedAt = System.currentTimeMillis() - 3_600_000L,
            ).apply { id = 3 },
        )
    )

    fun getAllSavedPages(): Flow<List<SavedPage>> = savedPages

    fun deleteSavedPage(savedPage: SavedPage) {
        viewModelScope.launch {
            savedPages.value = savedPages.value.filter { it.id != savedPage.id }
        }
    }
}
