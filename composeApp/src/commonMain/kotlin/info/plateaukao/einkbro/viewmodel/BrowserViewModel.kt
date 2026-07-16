package info.plateaukao.einkbro.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.browser.WebViewEngineListener
import info.plateaukao.einkbro.browser.createWebViewEngine
import info.plateaukao.einkbro.database.Record
import info.plateaukao.einkbro.database.RecordType
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.Album

/**
 * Phase-1 tab orchestration: the common-code half of Android's
 * TabManager/BrowserContainer. History is in-memory until Room lands (Phase 2).
 */
class BrowserViewModel : ViewModel(), WebViewEngineListener {

    val albums = mutableStateOf<List<Album>>(emptyList())
    val focusIndex = mutableStateOf(0)

    val currentTitle = mutableStateOf("")
    val currentUrl = mutableStateOf("")
    val progress = mutableStateOf(1f)

    val records = mutableStateListOf<Record>()

    private val engines = LinkedHashMap<Int, WebViewEngine>()

    val currentAlbum: Album? get() = albums.value.getOrNull(focusIndex.value)
    val currentEngine: WebViewEngine? get() = currentAlbum?.let { engines[it.id] }

    fun ensureFirstTab(homeUrl: String = DEFAULT_HOME) {
        if (albums.value.isEmpty()) newTab(homeUrl)
    }

    fun newTab(url: String, activate: Boolean = true) {
        val album = Album(
            title = "New tab",
            onShow = { switchTab(it) },
            onRemove = { closeTab(it) },
        )
        val engine = createWebViewEngine(album, this)
        engines[album.id] = engine
        albums.value = albums.value + album
        if (activate) {
            focusIndex.value = albums.value.lastIndex
            syncCurrentState()
        }
        if (url.isNotBlank()) engine.loadUrl(url)
    }

    fun switchTab(album: Album) {
        val index = albums.value.indexOfFirst { it.id == album.id }
        if (index >= 0) {
            currentEngine?.pause()
            focusIndex.value = index
            currentEngine?.resume()
            syncCurrentState()
        }
    }

    fun closeTab(album: Album) {
        val list = albums.value.toMutableList()
        val index = list.indexOfFirst { it.id == album.id }
        if (index < 0) return
        engines.remove(album.id)?.destroy()
        list.removeAt(index)
        albums.value = list
        if (list.isEmpty()) {
            newTab(DEFAULT_HOME)
        } else {
            focusIndex.value = index.coerceAtMost(list.lastIndex)
            syncCurrentState()
        }
    }

    /** URL bar submission: scheme/host heuristics, else search engine query. */
    fun loadUrlOrSearch(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("file://") || trimmed.startsWith("about:") -> trimmed

            !trimmed.contains(' ') && trimmed.contains('.') -> "https://$trimmed"

            else -> {
                val template = AppServices.config.browser.searchEngineUrl
                    .ifBlank { "https://www.google.com/search?q=%s" }
                template.replace("%s", percentEncode(trimmed))
            }
        }
        currentEngine?.loadUrl(url)
    }

    // --- WebViewEngineListener ---

    override fun onTitleChanged(engine: WebViewEngine, title: String) {
        engine.album.albumTitle = title
        if (engine === currentEngine) currentTitle.value = title
    }

    override fun onUrlChanged(engine: WebViewEngine, url: String) {
        if (engine === currentEngine) currentUrl.value = url
    }

    override fun onProgressChanged(engine: WebViewEngine, progress: Float) {
        if (engine === currentEngine) this.progress.value = progress
    }

    override fun onPageFinished(engine: WebViewEngine, url: String, title: String) {
        engine.album.isLoaded = true
        if (url.isBlank() || url == "about:blank") return
        records.removeAll { it.url == url }
        records.add(
            Record(
                title = title.ifBlank { url },
                url = url,
                time = System.currentTimeMillis(),
                type = RecordType.History,
            )
        )
    }

    private fun syncCurrentState() {
        currentTitle.value = currentAlbum?.albumTitle.orEmpty()
        currentUrl.value = currentEngine?.let { currentUrl.value } ?: ""
        progress.value = 1f
    }

    private fun percentEncode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-._~") append(c)
            else append('%').append(b.toUByte().toString(16).uppercase().padStart(2, '0'))
        }
    }

    companion object {
        const val DEFAULT_HOME = "https://en.wikipedia.org"
    }
}
