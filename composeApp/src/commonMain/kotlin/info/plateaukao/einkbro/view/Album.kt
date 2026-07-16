package info.plateaukao.einkbro.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Stand-in for the WebView-backed Album (browser tab). Carries just the state
 * the tab/history UIs render; browser callbacks become no-op lambdas the
 * catalog can override to log interactions.
 */
class Album(
    title: String = "",
    private val url: String = "",
    var onShow: (Album) -> Unit = {},
    var onRemove: (Album) -> Unit = {},
) {
    val id: Int = nextAlbumId++

    var isLoaded = false
    var isTranslatePage = false
    var albumTitle: String by mutableStateOf(title)
    var bitmap: ImageBitmap? by mutableStateOf(null)
    var isActivated = false

    fun showOrJumpToTop() = onShow(this)

    fun remove(showHomePage: Boolean = false) = onRemove(this)

    fun getUrl(): String = url

    fun setAlbumCover(bitmap: ImageBitmap?) {
        this.bitmap = bitmap
    }

    fun activate() {
        isActivated = true
    }

    fun deactivate() {
        isActivated = false
    }

    companion object {
        private var nextAlbumId = 0
    }
}
