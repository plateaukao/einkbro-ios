package info.plateaukao.einkbro.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** What a tab renders: a web engine, or a native chat surface (chat-with-web /
 *  agent chat — Android hosts these as chat.html web tabs; iOS renders them
 *  natively, so the tab layer needs to know which pane to mount). */
enum class AlbumType { Web, Chat }

/**
 * Stand-in for the WebView-backed Album (browser tab). Carries just the state
 * the tab/history UIs render; browser callbacks become no-op lambdas the
 * catalog can override to log interactions.
 */
class Album(
    title: String = "",
    private val url: String = "",
    val type: AlbumType = AlbumType.Web,
    var onShow: (Album) -> Unit = {},
    var onRemove: (Album) -> Unit = {},
) {
    val id: Int = nextAlbumId++

    var isLoaded = false
    var isTranslatePage = false
    var incognito = false
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
