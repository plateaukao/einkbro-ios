package info.plateaukao.einkbro.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.browser.WebViewEngineListener
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.ContentBlocker
import info.plateaukao.einkbro.browser.createWebViewEngine
import info.plateaukao.einkbro.view.WebContentHelper
import info.plateaukao.einkbro.database.HistoryRecord
import info.plateaukao.einkbro.database.Record
import info.plateaukao.einkbro.preference.AlbumInfo
import info.plateaukao.einkbro.preference.SaveHistoryMode
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.Album
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    // Phase 5 interaction: current text selection (null = none) and the URL of
    // a long-pressed link (null = no context menu). Both drive BrowserScreen
    // overlays and always reflect the active tab.
    val selectionInfo = mutableStateOf<SelectionInfo?>(null)
    val contextMenuLink = mutableStateOf<String?>(null)

    private val engines = LinkedHashMap<Int, WebViewEngine>()
    private val helpers = LinkedHashMap<Int, WebContentHelper>()
    private val config = AppServices.config
    private val historyDao = AppServices.database.historyDao()
    private val json = Json { ignoreUnknownKeys = true }

    /** Shared native side of the paragraph-translation JS bridge (Phase 6). */
    val translationBridge = info.plateaukao.einkbro.service.TranslationBridge()

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

    fun newTab(
        url: String,
        activate: Boolean = true,
        title: String = "New tab",
        incognito: Boolean = config.isIncognitoMode,
    ) {
        // Opening/activating a tab must not carry over the previous tab's
        // selection menu or link context menu.
        selectionInfo.value = null
        contextMenuLink.value = null
        val album = Album(
            title = title,
            onShow = { switchTab(it) },
            onRemove = { closeTab(it) },
        )
        album.incognito = incognito
        val engine = createWebViewEngine(album, this, incognito)
        engines[album.id] = engine
        helpers[album.id] = WebContentHelper(engine, config)
        if (Assets.isLoaded) {
            engine.installUserScript(Assets.get("fix_scrolling.js"), atDocumentStart = false)
            if (!config.browser.enableVideoAutoplay) {
                engine.installUserScript(
                    Assets.get("disable_video_autoplay.js"), atDocumentStart = true
                )
            }
            registerInteractionBridge(engine)
        }
        albums.value = albums.value + album
        if (activate) {
            focusIndex.value = albums.value.lastIndex
            syncCurrentState()
        }
        applyWebConfig(engine, url.ifBlank { DEFAULT_HOME })
        if (url.isNotBlank()) engine.loadUrl(url)
        persistTabs()
    }

    /** Applies per-domain user agent, JavaScript, and adblock to [engine]. */
    private fun applyWebConfig(engine: WebViewEngine, url: String) {
        val ua = when {
            config.getDesktopMode(url) -> DESKTOP_USER_AGENT
            config.browser.enableCustomUserAgent &&
                !config.browser.customUserAgent.isNullOrBlank() -> config.browser.customUserAgent
            else -> null
        }
        engine.setUserAgent(ua)
        engine.setJavaScriptEnabled(config.getEnableJavascript(url))
        engine.setAdBlockEnabled(ContentBlocker.isReady && config.getEnableAdBlock(url))
    }

    /** Re-applies web config to every open tab (after a toggle or adblock compile). */
    fun reapplyWebConfig() {
        engines.values.forEach { engine ->
            applyWebConfig(engine, engine.currentUrl().orEmpty().ifBlank { DEFAULT_HOME })
        }
    }

    /**
     * Wires the JS-to-Kotlin channels (selection reporting + link long-press)
     * and installs the page scripts that feed them. Only the active tab's
     * events surface in the UI state.
     */
    private fun registerInteractionBridge(engine: WebViewEngine) {
        engine.addMessageHandler("einkbroSelection") { body ->
            // Ignore the trailing selectionchange events a link long-press emits
            // while its context menu is up.
            if (engine !== currentEngine || contextMenuLink.value != null) {
                return@addMessageHandler
            }
            val payload = runCatching {
                json.decodeFromString<SelectionPayload>(body)
            }.getOrNull() ?: return@addMessageHandler
            selectionInfo.value = if (payload.text.isBlank()) null
            else SelectionInfo(
                payload.text, payload.left, payload.top, payload.right, payload.bottom
            )
        }
        engine.addMessageHandler("einkbroLongPress") { body ->
            if (engine !== currentEngine) return@addMessageHandler
            val payload = runCatching {
                json.decodeFromString<LinkPayload>(body)
            }.getOrNull() ?: return@addMessageHandler
            if (payload.url.isNotBlank()) {
                // A link long-press also starts a native word selection; hide our
                // selection menu so only the link context menu is shown.
                selectionInfo.value = null
                contextMenuLink.value = payload.url
            }
        }
        engine.installUserScript(Assets.get("selection_change.js"), atDocumentStart = false)
        engine.installUserScript(Assets.get("link_longpress.js"), atDocumentStart = false)
        translationBridge.attach(engine)
    }

    /** Highlights the active tab's selection and persists it (text-only). */
    fun highlightCurrentSelection() {
        val text = selectionInfo.value?.text ?: return
        currentHelper?.highlightSelection()
        val url = currentUrl.value
        val title = currentAlbum?.albumTitle.orEmpty()
        selectionInfo.value = null
        viewModelScope.launch {
            AppServices.bookmarkManager.saveHighlight(url, title, text)
        }
    }

    fun clearSelection() {
        selectionInfo.value = null
    }

    // --- export / offline (Phase 7) ---

    /** Renders the current page to a PDF and opens the iOS share sheet. */
    fun saveAsPdf(onResult: (Boolean) -> Unit) {
        val engine = currentEngine ?: return onResult(false)
        val name = info.plateaukao.einkbro.util.sanitizeFileName(currentTitle.value.ifBlank { "page" })
        engine.createPdf { bytes ->
            if (bytes == null) return@createPdf onResult(false)
            val path = info.plateaukao.einkbro.util.FileStore.writeBytes("pdf", "$name.pdf", bytes)
            if (path != null) {
                info.plateaukao.einkbro.util.FileStore.share(path)
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    /** Snapshots the current page to a .webarchive and records it (saved pages). */
    fun saveWebArchive(onResult: (Boolean) -> Unit) {
        val engine = currentEngine ?: return onResult(false)
        val title = currentTitle.value.ifBlank { currentUrl.value }
        val name = info.plateaukao.einkbro.util.sanitizeFileName(title)
        val url = currentUrl.value
        engine.createWebArchive { bytes ->
            if (bytes == null) return@createWebArchive onResult(false)
            val path = info.plateaukao.einkbro.util.FileStore.writeBytes(
                "saved_pages", "${name}_${System.currentTimeMillis()}.webarchive", bytes,
            )
            if (path == null) return@createWebArchive onResult(false)
            viewModelScope.launch {
                AppServices.bookmarkManager.insertSavedPage(
                    info.plateaukao.einkbro.database.SavedPage(
                        title = title, url = url, filePath = path,
                        savedAt = System.currentTimeMillis(),
                    )
                )
                onResult(true)
            }
        }
    }

    /** Opens an offline saved page (.webarchive) in a new tab. */
    fun openSavedPage(filePath: String, title: String) {
        newTab(url = "", title = title)
        currentEngine?.loadFile(filePath)
    }

    /** Opens a search for [query] in a fresh tab (selection-menu Search). */
    fun searchInNewTab(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val template = config.browser.searchEngineUrl
            .ifBlank { "https://www.google.com/search?q=%s" }
        newTab(template.replace("%s", percentEncode(trimmed)))
    }

    fun switchTab(album: Album) {
        val index = albums.value.indexOfFirst { it.id == album.id }
        if (index >= 0) {
            currentEngine?.pause()
            focusIndex.value = index
            currentEngine?.resume()
            // Stale selection/menu from the previous tab must not linger.
            selectionInfo.value = null
            contextMenuLink.value = null
            syncCurrentState()
            persistTabs()
        }
    }

    /** Cycles to the previous tab (wraps, like Android's gotoLeftTab). */
    fun gotoLeftTab() {
        val list = albums.value
        if (list.size < 2) return
        val i = focusIndex.value - 1
        switchTab(list[if (i < 0) list.lastIndex else i])
    }

    /** Cycles to the next tab (wraps). */
    fun gotoRightTab() {
        val list = albums.value
        if (list.size < 2) return
        val i = focusIndex.value + 1
        switchTab(list[if (i > list.lastIndex) 0 else i])
    }

    fun closeTab(album: Album) {
        val list = albums.value.toMutableList()
        val index = list.indexOfFirst { it.id == album.id }
        if (index < 0) return
        engines.remove(album.id)?.destroy()
        helpers.remove(album.id)
        list.removeAt(index)
        albums.value = list
        selectionInfo.value = null
        contextMenuLink.value = null
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
        currentEngine?.let { engine ->
            applyWebConfig(engine, url)
            engine.loadUrl(url)
        }
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
        // Incognito (per-tab or global) leaves no history trace.
        if (engine.incognito || config.isIncognitoMode) return
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
        // Incognito tabs leave no trace, so they never persist across launches.
        config.tab.savedAlbumInfoList = albums.value
            .filterNot { it.incognito }
            .map { album ->
                AlbumInfo(
                    title = album.albumTitle,
                    url = engines[album.id]?.currentUrl() ?: "",
                )
            }.filter { it.url.isNotBlank() }
        config.tab.currentAlbumIndex = focusIndex.value.coerceAtMost(albums.value.lastIndex)
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

        // Desktop Safari UA (mirrors Android's UA_DESKTOP_PREFIX switch).
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4.1 Safari/605.1.15"
    }
}

/** Active text selection with its bounding rect in the page's CSS pixels. */
data class SelectionInfo(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
private data class SelectionPayload(
    val text: String = "",
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

@Serializable
private data class LinkPayload(
    val url: String = "",
    val text: String = "",
)
