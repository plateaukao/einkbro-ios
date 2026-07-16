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
import info.plateaukao.einkbro.view.EBToast
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

    // Parity Phase B: engine-delegate requests that need host UI. Each is a
    // one-shot responder; BrowserScreen renders a dialog while non-null.
    val pendingAuthRequest = mutableStateOf<info.plateaukao.einkbro.browser.AuthRequest?>(null)
    val pendingSslError = mutableStateOf<info.plateaukao.einkbro.browser.SslErrorRequest?>(null)
    val pendingJsDialog = mutableStateOf<info.plateaukao.einkbro.browser.JsDialogRequest?>(null)

    // Parity Phase C: a tab awaiting close confirmation (confirmTabClose pref).
    val pendingTabClose = mutableStateOf<Album?>(null)

    private val engines = LinkedHashMap<Int, WebViewEngine>()
    private val helpers = LinkedHashMap<Int, WebContentHelper>()
    // Background tabs whose load was deferred (enableWebBkgndLoad off); the
    // URL loads the first time the tab is activated.
    private val pendingLoads = LinkedHashMap<Int, String>()
    private val config = AppServices.config
    private val historyDao = AppServices.database.historyDao()
    private val json = Json { ignoreUnknownKeys = true }

    /** Shared native side of the paragraph-translation JS bridge (Phase 6). */
    val translationBridge = info.plateaukao.einkbro.service.TranslationBridge()

    /** Native side of the userscript GM_* runtime (parity Phase H). */
    val userScriptBridge = info.plateaukao.einkbro.userscript.UserScriptBridge(
        AppServices.userScriptManager,
        viewModelScope,
    ) { url, active -> newTab(url, activate = active) }

    val currentAlbum: Album? get() = albums.value.getOrNull(focusIndex.value)
    val currentEngine: WebViewEngine? get() = currentAlbum?.let { engines[it.id] }
    val currentHelper: WebContentHelper? get() = currentAlbum?.let { helpers[it.id] }

    // Split screen (parity Phase G): a second engine shown beside the current
    // tab. Held OUTSIDE `albums` so it is not a tab-strip entry; its listener
    // callbacks are naturally ignored for the main URL bar (filtered by
    // `=== currentEngine`). Orientation/scroll-sync mirror TranslationConfig.
    val splitAlbum = mutableStateOf<Album?>(null)
    val splitOrientation = mutableStateOf(config.translation.translationOrientation)
    private val splitScrollEngines = mutableSetOf<Int>()

    val splitEngine: WebViewEngine? get() = splitAlbum.value?.let { engines[it.id] }
    val splitHelper: WebContentHelper? get() = splitAlbum.value?.let { helpers[it.id] }

    init {
        viewModelScope.launch { reloadRecords() }
        // Load installed userscripts so the first page can inject matching ones.
        viewModelScope.launch { AppServices.userScriptManager.reload() }
    }

    /** Restores the previous session's tabs, or opens the configured home. */
    fun ensureFirstTab(homeUrl: String = config.favoriteUrl.ifBlank { DEFAULT_HOME }) {
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
        // Behavior pref: a background tab only preloads when background loading
        // is enabled; otherwise defer the load until the tab is first shown.
        if (url.isNotBlank()) {
            if (activate || config.tab.enableWebBkgndLoad) {
                engine.loadUrl(url)
            } else {
                pendingLoads[album.id] = url
            }
        }
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
        // Display prefs (parity Phase C): dark-mode override + pinch zoom.
        engine.setDarkMode(
            when (config.display.darkMode) {
                info.plateaukao.einkbro.preference.DarkMode.FORCE_ON -> true
                info.plateaukao.einkbro.preference.DarkMode.DISABLED -> false
                info.plateaukao.einkbro.preference.DarkMode.SYSTEM -> null
            }
        )
        engine.setZoomEnabled(config.display.enableZoom)
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
        userScriptBridge.attach(engine)
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

    // --- userscripts (parity Phase H) ---

    /** GM_registerMenuCommand entries (caption→fnId) for the current page. */
    val userScriptMenuCommands: List<Pair<String, String>>
        get() = currentEngine?.let { userScriptBridge.menuCommands(it) }.orEmpty()

    /** Runs a registered userscript menu command on the current page. */
    fun invokeUserScriptMenuCommand(fnId: String) {
        currentEngine?.let { userScriptBridge.invokeMenu(it, fnId) }
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

    // --- EPUB export (parity Phase I) ---

    private val epubExporter = info.plateaukao.einkbro.epub.EpubExporter()

    /** 0..100 while an EPUB export runs; null when idle. Drives the dialog's progress. */
    val epubProgress = mutableStateOf<Int?>(null)

    /** Bumped each time an export finishes (any outcome); the dialog dismisses on change. */
    val epubDoneTick = mutableStateOf(0)

    /**
     * Captures the current page as an EPUB chapter and writes a new book (or
     * appends to [appendToPath] and rewrites it). Reader HTML + embedded images.
     */
    fun exportEpub(bookName: String, chapterTitle: String, appendToPath: String?) {
        val helper = currentHelper ?: return
        val url = currentUrl.value
        epubProgress.value = 0
        helper.getEpubChapter { raw ->
            val capture = epubExporter.parseCapture(raw)
            if (capture == null || capture.error != null || capture.xhtml.isBlank()) {
                epubProgress.value = null
                epubDoneTick.value++
                EBToast.show(AppServices.context, "Couldn't capture page for EPUB")
                return@getEpubChapter
            }
            viewModelScope.launch {
                val result = epubExporter.export(
                    capture, bookName, chapterTitle, url, appendToPath,
                ) { p -> epubProgress.value = p }
                epubProgress.value = null
                epubDoneTick.value++
                when (result) {
                    is info.plateaukao.einkbro.epub.ExportResult.Success -> {
                        if (appendToPath == null) {
                            config.addSavedEpubFile(
                                info.plateaukao.einkbro.preference.SavedFileInfo(
                                    result.bookTitle, result.path,
                                )
                            )
                        }
                        EBToast.show(AppServices.context, "Saved EPUB: ${result.bookTitle}")
                    }
                    is info.plateaukao.einkbro.epub.ExportResult.Failure ->
                        EBToast.show(AppServices.context, "EPUB failed: ${result.message}")
                }
            }
        }
    }

    fun removeSavedEpub(info: info.plateaukao.einkbro.preference.SavedFileInfo) {
        config.removeSavedEpubFile(info)
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
        newTab(searchUrlFor(trimmed))
    }

    /** Builds the search URL for [query] honoring the chosen search engine. */
    private fun searchUrlFor(query: String): String {
        val ordinal = config.browser.searchEngine.toIntOrNull()
            ?: info.plateaukao.einkbro.search.SearchEngine.GOOGLE.ordinal
        return info.plateaukao.einkbro.search.SearchEngineUrls.searchUrl(
            ordinal = ordinal,
            encodedQuery = percentEncode(query),
            customTemplate = config.browser.searchEngineUrl
                .ifBlank { "https://www.google.com/search?q=%s" },
        )
    }

    fun switchTab(album: Album) {
        val index = albums.value.indexOfFirst { it.id == album.id }
        if (index >= 0) {
            currentEngine?.pause()
            focusIndex.value = index
            currentEngine?.resume()
            // A deferred background tab loads the first time it's shown.
            pendingLoads.remove(album.id)?.let { url ->
                engines[album.id]?.loadUrl(url)
            }
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

    /**
     * Closes [album]. When [confirmTabClose] is set the host is asked to
     * confirm first (via [pendingTabClose]); the confirmed close then calls
     * back in here. [shouldShowNextAfterRemoveTab] picks whether focus lands
     * on the following tab or the previous one.
     */
    fun closeTab(album: Album) {
        if (config.tab.confirmTabClose && pendingTabClose.value?.id != album.id) {
            pendingTabClose.value = album
            return
        }
        pendingTabClose.value = null
        val list = albums.value.toMutableList()
        val index = list.indexOfFirst { it.id == album.id }
        if (index < 0) return
        engines.remove(album.id)?.destroy()
        helpers.remove(album.id)
        pendingLoads.remove(album.id)
        list.removeAt(index)
        albums.value = list
        selectionInfo.value = null
        contextMenuLink.value = null
        if (list.isEmpty()) {
            newTab(config.favoriteUrl.ifBlank { DEFAULT_HOME })
        } else {
            val next = if (config.tab.shouldShowNextAfterRemoveTab) index else index - 1
            focusIndex.value = next.coerceIn(0, list.lastIndex)
            currentEngine?.resume()
            syncCurrentState()
        }
        persistTabs()
    }

    /** Confirms a pending tab close (host tapped OK on the confirm dialog). */
    fun confirmPendingTabClose() {
        pendingTabClose.value?.let { closeTab(it) }
    }

    // --- Split screen (parity Phase G) ---

    /**
     * Menu/link/bookmark entry. With the pane already open: a non-null [url]
     * loads into it; a null [url] toggles it off. Closed: opens the second pane
     * showing [url] (or the current page).
     */
    fun toggleSplitScreen(url: String?) {
        if (splitAlbum.value != null) {
            if (url != null) splitEngine?.loadUrl(url) else closeSplitScreen()
            return
        }
        val target = url ?: currentUrl.value.ifBlank { config.favoriteUrl.ifBlank { DEFAULT_HOME } }
        val album = Album(title = "Split", onShow = {}, onRemove = { closeSplitScreen() })
        album.incognito = config.isIncognitoMode
        val engine = createWebViewEngine(album, this, album.incognito)
        engines[album.id] = engine
        helpers[album.id] = WebContentHelper(engine, config)
        if (Assets.isLoaded) {
            engine.installUserScript(Assets.get("fix_scrolling.js"), atDocumentStart = false)
        }
        splitAlbum.value = album
        applyWebConfig(engine, target)
        engine.loadUrl(target)
        bindSplitScrollReporter()
    }

    fun closeSplitScreen() {
        val album = splitAlbum.value ?: return
        engines.remove(album.id)?.destroy()
        helpers.remove(album.id)
        splitAlbum.value = null
    }

    /** Rotate between side-by-side (Horizontal) and stacked (Vertical). */
    fun toggleSplitOrientation() {
        val next = if (splitOrientation.value == info.plateaukao.einkbro.view.Orientation.Horizontal)
            info.plateaukao.einkbro.view.Orientation.Vertical
        else info.plateaukao.einkbro.view.Orientation.Horizontal
        splitOrientation.value = next
        config.translation.translationOrientation = next
    }

    /** Swap the two panes' content (long-press orientation on Android). */
    fun swapSplitPanes() {
        val split = splitEngine ?: return
        val main = currentEngine ?: return
        val mainUrl = main.currentUrl().orEmpty()
        val splitUrl = split.currentUrl().orEmpty()
        if (splitUrl.isNotBlank() && splitUrl != "about:blank") main.loadUrl(splitUrl)
        if (mainUrl.isNotBlank() && mainUrl != "about:blank") split.loadUrl(mainUrl)
    }

    /** Split-pane font +/- (WKWebView has no textZoom; scale via CSS). */
    fun adjustSplitFont(delta: Int) {
        splitEngine?.evaluateJavascript(
            "(function(){var e=document.documentElement;" +
                "var c=parseInt(e.style.webkitTextSizeAdjust)||100;" +
                "e.style.webkitTextSizeAdjust=Math.max(20,c+($delta))+'%';})();"
        )
    }

    /** Install the main pane's scroll reporter once so scroll-sync can mirror it. */
    private fun bindSplitScrollReporter() {
        val engine = currentEngine ?: return
        val id = engine.album.id
        if (id in splitScrollEngines) return
        splitScrollEngines += id
        engine.addMessageHandler("einkbroSplitScroll") { payload ->
            if (config.translation.translationScrollSync) {
                val y = payload.toDoubleOrNull() ?: return@addMessageHandler
                splitEngine?.evaluateJavascript("window.scrollTo(0, $y);")
            }
        }
        if (Assets.isLoaded) {
            engine.installUserScript(
                Assets.get("split_scroll_report.js"), atDocumentStart = false
            )
            engine.evaluateJavascript(Assets.get("split_scroll_report.js"))
        }
    }

    /** URL bar submission: scheme/host heuristics, else search engine query. */
    fun loadUrlOrSearch(input: String) {
        var trimmed = input.trim()
        // Behavior pref: strip pasted prefix junk before the real scheme.
        if (config.browser.shouldTrimInputUrl) {
            trimmed = info.plateaukao.einkbro.util.UrlTidy.trimBeforeScheme(trimmed)
        }
        if (trimmed.isEmpty()) return
        var url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("file://") || trimmed.startsWith("about:") -> trimmed

            !trimmed.contains(' ') && trimmed.contains('.') -> "https://$trimmed"

            else -> searchUrlFor(trimmed)
        }
        // Behavior pref: drop known tracking query parameters.
        if (config.browser.shouldPruneQueryParameters) {
            url = info.plateaukao.einkbro.util.UrlTidy.pruneQueryParameters(url)
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
        // Inject enabled userscripts that match this page (parity Phase H).
        userScriptBridge.onPageFinished(engine)
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

    // --- WebViewEngineListener: engine-delegate depth (parity Phase B) ---

    override fun onNewWindowRequested(engine: WebViewEngine, url: String) {
        // window.open / target=_blank / popup: open as a regular tab.
        newTab(url)
    }

    override fun onAuthChallenge(
        engine: WebViewEngine,
        request: info.plateaukao.einkbro.browser.AuthRequest,
    ) {
        pendingAuthRequest.value = request
    }

    override fun onSslError(
        engine: WebViewEngine,
        request: info.plateaukao.einkbro.browser.SslErrorRequest,
    ) {
        if (config.browser.enableCertificateErrorDialog) {
            pendingSslError.value = request
        } else {
            // Dialog disabled: fail the load like Android's silent SSL error.
            request.respond(false)
            EBToast.show(AppServices.context, "Blocked: untrusted certificate (${request.host})")
        }
    }

    override fun onJsDialog(
        engine: WebViewEngine,
        request: info.plateaukao.einkbro.browser.JsDialogRequest,
    ) {
        pendingJsDialog.value = request
    }

    override fun onDownloadStarted(engine: WebViewEngine, fileName: String) {
        EBToast.show(AppServices.context, "Downloading $fileName…")
    }

    override fun onDownloadFinished(engine: WebViewEngine, fileName: String, path: String?) {
        if (path != null) {
            EBToast.show(AppServices.context, "Downloaded $fileName")
            info.plateaukao.einkbro.util.FileStore.share(path)
        } else {
            EBToast.show(AppServices.context, "Download failed")
        }
    }

    override fun onLoadError(engine: WebViewEngine, description: String) {
        if (engine === currentEngine) {
            EBToast.show(AppServices.context, description)
        }
    }

    override fun shouldRouteLinkToSplit(engine: WebViewEngine, url: String): Boolean {
        // "Link here": a main-pane link tap loads in the open second pane instead.
        if (engine !== currentEngine) return false
        val split = splitAlbum.value ?: return false
        if (!config.translation.twoPanelLinkHere) return false
        engines[split.id]?.loadUrl(url)
        return true
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
                    // A deferred background tab hasn't loaded yet — persist its
                    // pending URL so it survives a relaunch.
                    url = engines[album.id]?.currentUrl()?.takeIf { it.isNotBlank() }
                        ?: pendingLoads[album.id] ?: "",
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
