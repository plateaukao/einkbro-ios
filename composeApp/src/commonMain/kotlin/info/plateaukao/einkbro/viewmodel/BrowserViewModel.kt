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
import info.plateaukao.einkbro.view.AlbumType
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
    // SAVE_WHEN_CLOSE history mode: album id → (title, url) of its last page.
    private val pendingCloseHistory = mutableMapOf<Int, Pair<String, String>>()

    val pendingAuthRequest = mutableStateOf<info.plateaukao.einkbro.browser.AuthRequest?>(null)
    val pendingSslError = mutableStateOf<info.plateaukao.einkbro.browser.SslErrorRequest?>(null)
    val pendingJsDialog = mutableStateOf<info.plateaukao.einkbro.browser.JsDialogRequest?>(null)
    // .user.js navigation: URL to offer as a userscript install.
    val pendingUserScriptInstall = mutableStateOf<String?>(null)

    // Parity Phase C: a tab awaiting close confirmation (confirmTabClose pref).
    val pendingTabClose = mutableStateOf<Album?>(null)

    private val engines = LinkedHashMap<Int, WebViewEngine>()
    private val helpers = LinkedHashMap<Int, WebContentHelper>()
    // Native chat tabs (AlbumType.Chat): album id → conversation session. A chat
    // album has an entry here instead of in `engines`.
    private val chatSessions = LinkedHashMap<Int, ChatSession>()
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
    val currentChatSession: ChatSession? get() = currentAlbum?.let { chatSessions[it.id] }

    /** Live engine for a tab by album id — null once that tab is closed. Agent tasks
     *  resolve the originating tab through this (Android used a WeakReference). */
    fun engineForAlbumId(albumId: Int): WebViewEngine? = engines[albumId]

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
        // Ebook touch mode must (dis)arm the current page live when the touch
        // area type or the touch-turn toggle changes — no reload (the VM is a
        // session singleton, so the listener is never unregistered).
        config.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == info.plateaukao.einkbro.preference.TouchConfig.K_TOUCH_AREA_TYPE ||
                key == info.plateaukao.einkbro.preference.TouchConfig.K_ENABLE_TOUCH
            ) {
                currentHelper?.updateEbookTouchMode()
            }
        }
        pruneMissingSavedFiles()
        viewModelScope.launch { reloadRecords() }
        // Load installed userscripts so the first page can inject matching ones.
        viewModelScope.launch { AppServices.userScriptManager.reload() }
        // Hydrate per-site configuration (translation mode, auto-translate, per-site
        // display) from the DB so it survives relaunch (parity Phase M).
        viewModelScope.launch {
            val stored = AppServices.bookmarkManager.getAllDomainConfigurations()
            if (stored.isNotEmpty()) {
                config.domainConfigurationMap = stored.associateBy { it.domain }.toMutableMap()
            }
        }
    }

    /** Restores the previous session's tabs, or opens the configured home. */
    fun ensureFirstTab(homeUrl: String = config.favoriteUrl.ifBlank { DEFAULT_HOME }) {
        if (albums.value.isNotEmpty()) return
        val saved = if (config.tab.shouldSaveTabs) config.tab.savedAlbumInfoList else emptyList()
        if (saved.isEmpty()) {
            newTab(homeUrl)
            return
        }
        // Capture before the loop: newTab() -> persistTabs() rewrites currentAlbumIndex.
        val savedIndex = config.tab.currentAlbumIndex.coerceIn(0, saved.lastIndex)
        // Mirrors Android initSavedTabs: the previously-current tab comes back
        // foreground and loads right away; the others restore lazily and only
        // load when first shown.
        saved.forEachIndexed { index, info ->
            newTab(
                info.url,
                activate = index == savedIndex,
                title = info.title,
                lazyLoad = index != savedIndex,
            )
        }
    }

    fun newTab(
        url: String,
        activate: Boolean = true,
        title: String = "New tab",
        incognito: Boolean = config.isIncognitoMode,
        // A lazily restored tab defers its load until first shown, even when
        // background loading is enabled (Android's lazyLoad in loadUrlInWebView).
        lazyLoad: Boolean = false,
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
            if (activate || (config.tab.enableWebBkgndLoad && !lazyLoad)) {
                engine.loadUrl(url)
            } else {
                pendingLoads[album.id] = url
            }
        }
        persistTabs()
    }

    /**
     * Opens a native chat tab (chat-with-web / agent chat). The [session] is
     * built by the host (it needs TTS + tools wiring the view model doesn't
     * own) and is disposed when the tab closes. Android's equivalent is
     * addAlbum("Chat With Web"/"Agent Chat") + chat.html; here the tab renders
     * ChatTabContent instead of a web engine.
     */
    fun newChatTab(title: String, session: ChatSession) {
        selectionInfo.value = null
        contextMenuLink.value = null
        val album = Album(
            title = title,
            type = AlbumType.Chat,
            onShow = { switchTab(it) },
            onRemove = { closeTab(it) },
        )
        chatSessions[album.id] = session
        albums.value = albums.value + album
        currentEngine?.pause()
        focusIndex.value = albums.value.lastIndex
        syncCurrentState()
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
        // Privacy enforcement (parity Phase N): block images when disabled, strip
        // cookies when the per-site/global cookie setting is off, and expose the
        // web view to Safari Web Inspector when debug is on.
        engine.setImageBlockEnabled(!config.browser.enableImages)
        engine.setCookieBlockEnabled(!config.getEnableCookies(url))
        engine.setAnalyticsBlockEnabled(config.browser.blockAnalytics)
        engine.setInspectable(config.browser.debugWebView)
        // Display prefs (parity Phase C): dark-mode override + pinch zoom.
        engine.setDarkMode(
            when (config.display.darkMode) {
                info.plateaukao.einkbro.preference.DarkMode.FORCE_ON -> true
                info.plateaukao.einkbro.preference.DarkMode.DISABLED -> false
                info.plateaukao.einkbro.preference.DarkMode.SYSTEM -> null
            }
        )
        engine.setZoomEnabled(config.display.enableZoom)
        engine.setPullToRefreshEnabled(config.browser.enablePullToRefresh)
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
        // Ebook touch-area type: the page reports left/right-half taps (Android
        // intercepts these natively in EBWebView.dispatchTouchEvent). The pref
        // is re-checked here so a tab armed before the mode was switched off
        // can't page.
        engine.addMessageHandler("einkbroEbookTap") { side ->
            if (engine !== currentEngine) return@addMessageHandler
            if (!config.touch.isEbookModeActive) return@addMessageHandler
            val pageUp = (side == "left") != config.touch.switchTouchAreaAction
            if (pageUp) currentHelper?.pageUp() else currentHelper?.pageDown()
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
        // Dual YouTube captions (parity Phase M): a document-start fetch/XHR shim
        // that merges a second-language timedtext copy in-page. The locale is
        // baked in at engine creation; new tabs pick up a changed pref.
        val dualCaptionLocale = config.tts.dualCaptionLocale
        if (dualCaptionLocale.isNotBlank()) {
            engine.installUserScript(
                Assets.get("dual_caption_shim.js")
                    .replace("%%DUAL_CAPTION_LOCALE%%", dualCaptionLocale),
                atDocumentStart = true,
            )
        }
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
                        title = title, url = url,
                        // Container-relative: an absolute path stops resolving
                        // the moment the app is reinstalled.
                        filePath = info.plateaukao.einkbro.util.storedPathFor(path),
                        savedAt = System.currentTimeMillis(),
                    )
                )
                onResult(true)
            }
        }
    }

    // --- EPUB export (parity Phase I) ---

    private val epubExporter = info.plateaukao.einkbro.epub.EpubExporter()

    /**
     * Bumped when a page finishes loading, carrying the finished URL, so the UI
     * can auto-fire per-site translation (parity Phase M, shouldTranslateSite).
     */
    val pageFinishedTick = mutableStateOf(0)
    var lastFinishedUrl: String = ""
        private set

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
                // The stored path may predate this install's container.
                val target = appendToPath?.let { info.plateaukao.einkbro.util.resolveStoredPath(it) }
                val result = epubExporter.export(
                    capture, bookName, chapterTitle, url, target,
                ) { p -> epubProgress.value = p }
                epubProgress.value = null
                epubDoneTick.value++
                when (result) {
                    is info.plateaukao.einkbro.epub.ExportResult.Success -> {
                        if (appendToPath == null) {
                            config.addSavedEpubFile(
                                info.plateaukao.einkbro.preference.SavedFileInfo(
                                    result.bookTitle,
                                    info.plateaukao.einkbro.util.storedPathFor(result.path),
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

    /**
     * Drops saved EPUB/PDF entries whose file is gone: the Android
     * `content://` URIs older backup imports carried over (nothing on iOS can
     * open those), and any path left behind by an install whose container is
     * no longer reachable. Entries that still resolve are untouched.
     */
    private fun pruneMissingSavedFiles() {
        val epubs = config.savedEpubFileInfos
        val keptEpubs = epubs.filter { it.fileExists() }
        if (keptEpubs.size != epubs.size) config.savedEpubFileInfos = keptEpubs

        val pdfs = config.savedPdfFileInfos
        val keptPdfs = pdfs.filter { it.fileExists() }
        if (keptPdfs.size != pdfs.size) config.savedPdfFileInfos = keptPdfs
    }

    private fun info.plateaukao.einkbro.preference.SavedFileInfo.fileExists(): Boolean =
        info.plateaukao.einkbro.util.FileStore.exists(
            info.plateaukao.einkbro.util.resolveStoredPath(uri)
        )

    // --- Instapaper (parity Phase J) ---

    private val instapaperRepo = info.plateaukao.einkbro.data.remote.InstapaperRepository()

    fun hasInstapaperCredentials(): Boolean =
        config.instapaperUsername.isNotBlank() && config.instapaperPassword.isNotBlank()

    /** POSTs the current page to Instapaper's Simple API using the saved credentials. */
    fun addToInstapaper() {
        val url = currentUrl.value
        if (url.isBlank()) {
            EBToast.show(AppServices.context, "URL is empty")
            return
        }
        val title = currentTitle.value
        EBToast.show(AppServices.context, "Adding to Instapaper…")
        viewModelScope.launch {
            val result = instapaperRepo.addUrl(
                url, config.instapaperUsername, config.instapaperPassword, title,
            )
            val message = when (result) {
                is info.plateaukao.einkbro.data.remote.InstapaperResult.Success -> result.message
                is info.plateaukao.einkbro.data.remote.InstapaperResult.Error -> result.message
            }
            EBToast.show(AppServices.context, message)
        }
    }

    fun saveInstapaperCredentials(username: String, password: String) {
        config.instapaperUsername = username.trim()
        config.instapaperPassword = password.trim()
    }

    /** Opens an offline saved page (.webarchive) in a new tab. */
    fun openSavedPage(filePath: String, title: String) {
        val path = info.plateaukao.einkbro.util.resolveStoredPath(filePath)
        // Check before opening the tab: a missing file would otherwise leave a
        // blank tab behind and report WKWebView's "not found on this server".
        if (!info.plateaukao.einkbro.util.FileStore.exists(path)) {
            EBToast.show(AppServices.context, "Saved page file is missing")
            return
        }
        newTab(url = "", title = title)
        currentEngine?.loadFile(path)
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
            encodedQuery = info.plateaukao.einkbro.search.SearchEngineUrls.percentEncodeQuery(query),
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
            // Pages loaded before ebook mode was toggled have no (or a stale)
            // tap reporter — re-arm the newly shown tab.
            helpers[album.id]?.updateEbookTouchMode()
            syncCurrentState()
            persistTabs()
        }
    }

    /**
     * Tab-item tap (Android Album.showOrJumpToTop): a non-focused tab is
     * switched to; tapping the already-focused tab scrolls it to the top, or
     * reloads it when it is already at the top.
     */
    fun showOrJumpToTop(album: Album) {
        if (album.id == currentAlbum?.id) {
            val engine = currentEngine ?: return
            if (engine.isAtTop()) engine.reload() else engine.jumpToTop()
        } else {
            switchTab(album)
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
        // SAVE_WHEN_CLOSE: the deferred history record is written now.
        pendingCloseHistory.remove(album.id)?.let { (title, url) ->
            viewModelScope.launch {
                historyDao.deleteByUrl(url)
                historyDao.insert(
                    HistoryRecord(TITLE = title, URL = url, TIME = System.currentTimeMillis())
                )
                reloadRecords()
            }
        }
        engines.remove(album.id)?.destroy()
        helpers.remove(album.id)
        chatSessions.remove(album.id)?.dispose()
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
        // Signal the UI to auto-translate this site if the user marked it.
        if (engine === currentEngine) {
            lastFinishedUrl = url
            pageFinishedTick.value += 1
        }
        // Incognito (per-tab or global) leaves no history trace.
        if (engine.incognito || config.isIncognitoMode) return
        if (config.tab.saveHistoryMode == SaveHistoryMode.DISABLED) return
        if (config.tab.saveHistoryMode == SaveHistoryMode.SAVE_WHEN_CLOSE) {
            // Android TabManager defers the record until the tab closes; only
            // the tab's final page is kept.
            pendingCloseHistory[engine.album.id] = (title.ifBlank { url }) to url
            return
        }
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

    override fun onUserScriptInstallRequested(engine: WebViewEngine, url: String) {
        pendingUserScriptInstall.value = url
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
            // Chat tabs are conversation state, not URLs — they don't survive
            // relaunch (Android's chat.html tabs restore as blanks; we skip them).
            .filterNot { it.type == AlbumType.Chat }
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
