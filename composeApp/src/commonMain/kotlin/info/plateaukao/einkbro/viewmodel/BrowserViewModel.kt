package info.plateaukao.einkbro.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.browser.WebViewEngineListener
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.createWebViewEngine
import info.plateaukao.einkbro.view.WebContentHelper
import info.plateaukao.einkbro.database.HistoryRecord
import info.plateaukao.einkbro.database.Record
import info.plateaukao.einkbro.preference.AlbumInfo
import info.plateaukao.einkbro.preference.SaveHistoryMode
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.Album
import kotlinx.coroutines.launch

/**
 * Tab orchestration: the common-code half of Android's TabManager.
 * Phase 2: history is Room-backed; open tabs persist via TabConfig.
 */
class BrowserViewModel : ViewModel(), WebViewEngineListener {

    val albums = mutableStateOf<List<Album>>(emptyList())
    val focusIndex = mutableStateOf(0)

    val currentTitle = mutableStateOf("")
    val currentUrl = mutableStateOf("")
    val progress = mutableStateOf(1f)

    val records = mutableStateOf<List<Record>>(emptyList())

    private val engines = LinkedHashMap<Int, WebViewEngine>()
    private val helpers = LinkedHashMap<Int, WebContentHelper>()
    private val config = AppServices.config
    private val historyDao = AppServices.database.historyDao()

    val currentAlbum: Album? get() = albums.value.getOrNull(focusIndex.value)
    val currentEngine: WebViewEngine? get() = currentAlbum?.let { engines[it.id] }
    val currentHelper: WebContentHelper? get() = currentAlbum?.let { helpers[it.id] }

    init {
        viewModelScope.launch { reloadRecords() }
    }

    /** Restores the previous session's tabs, or opens the default home. */
    fun ensureFirstTab(homeUrl: String = DEFAULT_HOME) {
        if (albums.value.isNotEmpty()) return
        val saved = if (config.tab.shouldSaveTabs) config.tab.savedAlbumInfoList else emptyList()
        if (saved.isEmpty()) {
            newTab(homeUrl)
            return
        }
        saved.forEach { info ->
            newTab(info.url, activate = false, title = info.title)
        }
        focusIndex.value = config.tab.currentAlbumIndex.coerceIn(0, albums.value.lastIndex)
        syncCurrentState()
    }

    fun newTab(url: String, activate: Boolean = true, title: String = "New tab") {
        val album = Album(
            title = title,
            onShow = { switchTab(it) },
            onRemove = { closeTab(it) },
        )
        val engine = createWebViewEngine(album, this)
        engines[album.id] = engine
        helpers[album.id] = WebContentHelper(engine, config)
        if (Assets.isLoaded) {
            engine.installUserScript(Assets.get("fix_scrolling.js"), atDocumentStart = false)
            if (!config.browser.enableVideoAutoplay) {
                engine.installUserScript(
                    Assets.get("disable_video_autoplay.js"), atDocumentStart = true
                )
            }
        }
        albums.value = albums.value + album
        if (activate) {
            focusIndex.value = albums.value.lastIndex
            syncCurrentState()
        }
        if (url.isNotBlank()) engine.loadUrl(url)
        persistTabs()
    }

    fun switchTab(album: Album) {
        val index = albums.value.indexOfFirst { it.id == album.id }
        if (index >= 0) {
            currentEngine?.pause()
            focusIndex.value = index
            currentEngine?.resume()
            syncCurrentState()
            persistTabs()
        }
    }

    fun closeTab(album: Album) {
        val list = albums.value.toMutableList()
        val index = list.indexOfFirst { it.id == album.id }
        if (index < 0) return
        engines.remove(album.id)?.destroy()
        helpers.remove(album.id)
        list.removeAt(index)
        albums.value = list
        if (list.isEmpty()) {
            newTab(DEFAULT_HOME)
        } else {
            focusIndex.value = index.coerceAtMost(list.lastIndex)
            syncCurrentState()
        }
        persistTabs()
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
                val template = config.browser.searchEngineUrl
                    .ifBlank { "https://www.google.com/search?q=%s" }
                template.replace("%s", percentEncode(trimmed))
            }
        }
        currentEngine?.loadUrl(url)
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.deleteAll()
            reloadRecords()
        }
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
        albums.value.firstOrNull { it.id == engine.album.id }
            ?.let { helpers[it.id]?.onPageLoaded() }
        persistTabs()
        if (url.isBlank() || url == "about:blank") return
        if (config.isIncognitoMode) return
        if (config.tab.saveHistoryMode == SaveHistoryMode.DISABLED) return
        viewModelScope.launch {
            historyDao.deleteByUrl(url)
            historyDao.insert(
                HistoryRecord(
                    TITLE = title.ifBlank { url },
                    URL = url,
                    TIME = System.currentTimeMillis(),
                )
            )
            reloadRecords()
        }
    }

    private suspend fun reloadRecords() {
        records.value = historyDao.getAllHistory().map { it.toRecord() }
    }

    private fun persistTabs() {
        if (!config.tab.shouldSaveTabs) return
        config.tab.savedAlbumInfoList = albums.value.map { album ->
            AlbumInfo(
                title = album.albumTitle,
                url = engines[album.id]?.currentUrl() ?: "",
            )
        }.filter { it.url.isNotBlank() }
        config.tab.currentAlbumIndex = focusIndex.value
    }

    private fun syncCurrentState() {
        currentTitle.value = currentAlbum?.albumTitle.orEmpty()
        currentUrl.value = currentEngine?.currentUrl().orEmpty()
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
