package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.graphics.Point
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.util.NoDimDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.activity.HighlightsScreen
import info.plateaukao.einkbro.activity.SavedPagesScreen
import info.plateaukao.einkbro.activity.SettingsScreen
import info.plateaukao.einkbro.activity.UserScriptListScreen
import info.plateaukao.einkbro.view.dialog.compose.EpubDialog
import info.plateaukao.einkbro.view.dialog.compose.InstapaperDialog
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.browser.MultitouchDirection
import info.plateaukao.einkbro.browser.WebViewHost
import info.plateaukao.einkbro.catalog.DialogFrame
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionScope
import info.plateaukao.einkbro.preference.ShareLongPressAction
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.task_custom_hint
import info.plateaukao.einkbro.resources.task_custom_title
import info.plateaukao.einkbro.resources.ic_chat_gpt
import info.plateaukao.einkbro.resources.ic_highlight_color
import info.plateaukao.einkbro.util.PlatformActions
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.data.MenuInfo
import info.plateaukao.einkbro.view.dialog.compose.ActionModeMenu
import info.plateaukao.einkbro.view.dialog.compose.AuthenticationDialogContent
import info.plateaukao.einkbro.view.dialog.compose.BookmarksDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ContextMenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ContextMenuItemType
import info.plateaukao.einkbro.view.dialog.compose.FastToggleDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FontBoldnessContent
import info.plateaukao.einkbro.view.dialog.compose.FontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ReaderFontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.LanguageSettingDialogContent
import info.plateaukao.einkbro.view.dialog.compose.AnchoredDialogFrame
import info.plateaukao.einkbro.view.dialog.compose.PointAnchoredDialogFrame
import info.plateaukao.einkbro.view.dialog.compose.MenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.PageAiActionDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ReaderSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TocDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TocItem
import info.plateaukao.einkbro.view.dialog.compose.TouchAreaDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslateDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslationConfigDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TtsSettingDialogContent
import info.plateaukao.einkbro.view.handlers.MenuActionHandler
import info.plateaukao.einkbro.view.handlers.ToolbarActionHandler
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo
import info.plateaukao.einkbro.viewmodel.BrowserViewModel
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API
import info.plateaukao.einkbro.viewmodel.TranslationViewModel
import info.plateaukao.einkbro.viewmodel.TtsViewModel
import androidx.compose.runtime.snapshotFlow
import info.plateaukao.einkbro.database.Record
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase-1 browser: real WKWebView behind the ported EinkBro chrome.
 * Feature actions that belong to later phases surface a toast.
 */
@Composable
fun BrowserScreen(
    browserViewModel: BrowserViewModel,
) {
    val config = AppServices.config
    var showTabStrip by remember { mutableStateOf(config.tab.shouldShowTabBar) }
    var showOverview by remember { mutableStateOf(false) }
    var overviewShowsHistory by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Sub-screen the settings open on; Gesture when entered from the touch-area
    // dialog's "Touch action settings" row (Android: IntentUnit.gotoSettings).
    var settingsInitialRoute by remember {
        mutableStateOf(info.plateaukao.einkbro.activity.SettingRoute.Main)
    }
    var showFontDialog by remember { mutableStateOf(false) }
    // Reader mode keeps its own font size/type prefs (Android
    // DisplayConfigDelegate.showFontSizeChangeDialog); captured at open time.
    var fontDialogForReader by remember { mutableStateOf(false) }
    var showFastToggle by remember { mutableStateOf(false) }
    var showSiteSettings by remember { mutableStateOf(false) }
    var showTouchAreaDialog by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showSavedPages by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var translateDialogWholePage by remember { mutableStateOf(false) }
    var showTranslationConfig by remember { mutableStateOf(false) }
    var showBoldnessDialog by remember { mutableStateOf(false) }
    var showReaderSettings by remember { mutableStateOf(false) }
    var showToolbarConfig by remember { mutableStateOf(false) }
    var showMenuItemHide by remember { mutableStateOf(false) }
    var showPageAiActions by remember { mutableStateOf(false) }
    var showUserScripts by remember { mutableStateOf(false) }
    var showEpubDialog by remember { mutableStateOf(false) }
    var showInstapaperConfig by remember { mutableStateOf(false) }
    var showGptActions by remember { mutableStateOf(false) }
    var showGptQueries by remember { mutableStateOf(false) }
    // Settings sub-editors reachable from the settings screen (parity Phase N).
    // (showToolbarConfig already exists above for the IconSetting toolbar action.)
    var showStatusbarConfig by remember { mutableStateOf(false) }
    var showAdBlockSettings by remember { mutableStateOf(false) }
    var showWhitelist by remember {
        mutableStateOf<info.plateaukao.einkbro.activity.WhiteListType?>(null)
    }
    // Userscript GM_registerMenuCommand entries for the current page (parity Phase H).
    var userScriptCommands by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var languageConfigApi by remember { mutableStateOf<TRANSLATE_API?>(null) }
    var tocItems by remember { mutableStateOf<List<TocItem>?>(null) }
    var toolbarRefreshTick by remember { mutableStateOf(0) }

    // Mirror of Android BrowserActivity's onSharedPreferenceChanged block: UI
    // prefs apply live. Layout prefs bump the tick, which recomposes the screen
    // so composition-time config reads (statusbar, toolbar position/icons, FAB)
    // refresh without a restart.
    DisposableEffect(Unit) {
        val uiKeys = setOf(
            info.plateaukao.einkbro.preference.UiConfig.K_TOOLBAR_POSITION,
            info.plateaukao.einkbro.preference.UiConfig.K_TOOLBAR_TOP,
            info.plateaukao.einkbro.preference.UiConfig.K_HIDE_STATUSBAR,
            info.plateaukao.einkbro.preference.UiConfig.K_STATUSBAR_ENABLED,
            info.plateaukao.einkbro.preference.UiConfig.K_STATUSBAR_POSITION,
            info.plateaukao.einkbro.preference.UiConfig.K_STATUSBAR_ITEMS,
            info.plateaukao.einkbro.preference.UiConfig.K_TOOLBAR_ICONS,
            info.plateaukao.einkbro.preference.UiConfig.K_FAB_POSITION,
            info.plateaukao.einkbro.preference.UiConfig.K_NAV_POSITION,
            info.plateaukao.einkbro.preference.UiConfig.K_EDGE_TO_EDGE_TOOLBAR,
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                info.plateaukao.einkbro.preference.TabConfig.K_SHOW_TAB_BAR ->
                    showTabStrip = config.tab.shouldShowTabBar
                in uiKeys -> toolbarRefreshTick += 1
                // Android reloads the page for these (BrowserActivity L1124-31).
                info.plateaukao.einkbro.preference.BrowserConfig.K_DESKTOP,
                info.plateaukao.einkbro.preference.BrowserConfig.K_ENABLE_VIDEO_AUTOPLAY,
                info.plateaukao.einkbro.preference.BrowserConfig.K_ENABLE_CUSTOM_USER_AGENT,
                info.plateaukao.einkbro.preference.BrowserConfig.K_CUSTOM_USER_AGENT,
                info.plateaukao.einkbro.preference.DisplayConfig.K_DARK_MODE -> {
                    browserViewModel.reapplyWebConfig()
                    browserViewModel.currentEngine?.reload()
                }
                info.plateaukao.einkbro.preference.BrowserConfig.K_ENABLE_PULL_TO_REFRESH ->
                    browserViewModel.reapplyWebConfig()
                else -> Unit
            }
        }
        config.registerOnSharedPreferenceChangeListener(listener)
        onDispose { config.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var isFullscreen by remember { mutableStateOf(false) }
    // Auto-hide toolbar on scroll (Android shouldHideToolbar): hidden past a
    // downward-scroll threshold, restored on scroll-up or jump-to-top.
    var toolbarHiddenByScroll by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchResultInfo by remember { mutableStateOf("") }
    var touchPagingEnabled by remember { mutableStateOf(config.touch.enableTouchTurn) }
    val scope = rememberCoroutineScope()

    // Host pref (parity Phase D): keep-awake applies at startup and on change.
    LaunchedEffect(config.ui.keepAwake) {
        info.plateaukao.einkbro.util.HostBridge.setKeepAwake(config.ui.keepAwake)
    }
    // Status-bar hide / fullscreen: hide the system overlay via the host and
    // let the Compose root go edge-to-edge under the freed space. Keyed on the
    // pref tick (not the derived flag): hideStatusbar is a plain pref read, so
    // the tick is what recomposes this scope when the setting changes.
    val statusBarSuppressed = config.ui.hideStatusbar || isFullscreen
    LaunchedEffect(toolbarRefreshTick, isFullscreen) {
        info.plateaukao.einkbro.util.HostBridge.setStatusBarHidden(
            config.ui.hideStatusbar || isFullscreen
        )
    }
    // Edge-to-edge toolbar: bottom chrome may occupy the home-indicator band
    // only when the host defers the bottom-edge system gesture there (else the
    // system delays or steals its taps — the reason 011c5f0 was walked back).
    // Deferral costs a double-swipe to go Home, so it is on strictly while a
    // bottom toolbar is actually rendered in the band, and off on iOS 15 where
    // SwiftUI has no deferral lever (falls back to the Safari-style padding).
    val edgeToEdgeToolbar = config.ui.edgeToEdgeToolbar &&
        info.plateaukao.einkbro.util.HostBridge.supportsBottomGestureDeferral
    val bottomToolbarInBand = edgeToEdgeToolbar &&
        !isFullscreen && !toolbarHiddenByScroll && !showUrlInput &&
        (!config.ui.isToolbarOnTop || config.ui.isVerticalToolbar)
    LaunchedEffect(toolbarRefreshTick, bottomToolbarInBand) {
        info.plateaukao.einkbro.util.HostBridge.setDefersBottomSystemGesture(
            bottomToolbarInBand
        )
    }

    // Session-scoped services (Phase 6): reading continues after the TTS
    // dialog closes, and translate results survive reopening the popup.
    val ttsViewModel = remember { TtsViewModel() }
    var showTaskMenu by remember { mutableStateOf(false) }
    val taskRunner = remember {
        info.plateaukao.einkbro.task.TaskRunner(
            ttsViewModel = ttsViewModel,
            activeEngineProvider = { browserViewModel.currentEngine },
        )
    }
    val translationViewModel = remember { TranslationViewModel() }

    LaunchedEffect(Unit) {
        info.plateaukao.einkbro.browser.Assets.preload()
        // Compile the adblock rule list once; re-apply to tabs when it's ready.
        info.plateaukao.einkbro.browser.ContentBlocker.preload(
            info.plateaukao.einkbro.browser.Assets.get("adblock_rules.json")
        ) { browserViewModel.reapplyWebConfig() }
        // Compile the privacy content rules (image/cookie block, parity Phase N).
        info.plateaukao.einkbro.browser.ContentBlocker.preloadPrivacyRules {
            browserViewModel.reapplyWebConfig()
        }
        browserViewModel.ensureFirstTab()
    }

    // URLs opened from outside the app (einkbro:// scheme, http(s) hand-off,
    // .webarchive file open) arrive here and open in a fresh tab.
    LaunchedEffect(Unit) {
        info.plateaukao.einkbro.util.ExternalUrlBridge.urls.collect { url ->
            if (url.startsWith("file://")) {
                val path = url.removePrefix("file://")
                browserViewModel.openSavedPage(path, path.substringAfterLast('/'))
            } else {
                browserViewModel.newTab(url)
            }
        }
    }

    val engine = browserViewModel.currentEngine
    val helper = browserViewModel.currentHelper
    val progress by browserViewModel.progress

    /** Runs the chosen translation mode on the current page (Phase 6). */
    // AI task runner (Android TaskMenuDelegate parity): start the task, attach
    // the progress stream to the translate/AI result dialog, then show it.
    fun runTaskById(taskId: String) {
        val descriptor = info.plateaukao.einkbro.task.TaskCatalog.byId(taskId)
        if (descriptor == null) {
            EBToast.show(AppServices.context, "Unknown task")
            return
        }
        if (!translationViewModel.hasOpenAiApiKey() && !config.ai.useGeminiApi) {
            EBToast.show(AppServices.context, "Add OpenAI key in Settings")
            return
        }
        // Start first so the progress flow holds the fresh Running state before
        // the collector attaches (else the collector's first emission is the
        // previous task's stale Done value).
        taskRunner.run(descriptor.factory())
        translationViewModel.setupTaskStream(taskRunner.progress)
        translateDialogWholePage = true
        showTranslateDialog = true
    }

    /** Opens a chat-with-web tab: chat.html in a web tab driven by
     *  ChatWebInterface, seeded with [content] (Android's
     *  addAlbum("Chat With Web") + EBWebView.setupAiPage). */
    fun openChatWithWebTab(content: String, runAction: ChatGPTActionInfo?) {
        // Capture before the chat tab replaces the page as the active tab.
        val pageTitle = browserViewModel.currentTitle.value
        val pageUrl = browserViewModel.currentUrl.value
        val engine = browserViewModel.newAiTab("Chat With Web")
        val chatInterface = info.plateaukao.einkbro.browser.ChatWebInterface(
            scope = scope,
            engine = engine,
            webContent = content,
            webTitle = pageTitle,
            webUrl = pageUrl,
            onOpenNewTab = { url -> browserViewModel.newTab(url) },
        )
        browserViewModel.attachChatInterface(engine.album.id, chatInterface)
        chatInterface.loadChatPage(
            initialPrompt = runAction?.let { it.userMessage.ifBlank { it.systemMessage } },
        )
    }

    fun runCustomTask(prompt: String) {
        if (prompt.isBlank()) return
        if (config.ai.useGeminiApi) {
            EBToast.show(AppServices.context, "Custom tasks require OpenAI (tool-calling)")
            return
        }
        if (!translationViewModel.hasOpenAiApiKey()) {
            EBToast.show(AppServices.context, "Add OpenAI key in Settings")
            return
        }
        // Snapshot the page being viewed BEFORE the chat tab replaces it as the
        // active tab (Android TaskMenuDelegate.runCustomTask): the agent's
        // initial-page tools, live-tab javascript, and domain-config host all
        // key off it. The conversation then continues in an agent chat tab —
        // the user can reply to steer the agent (Android's chatWithWebAgent).
        scope.launch {
            val originEngine = browserViewModel.currentEngine
            val snapshot = originEngine?.let { origin ->
                val albumId = origin.album.id
                info.plateaukao.einkbro.task.SnapshotCapture.capture(origin) {
                    browserViewModel.engineForAlbumId(albumId)
                }
            }
            val engine = browserViewModel.newAiTab("Agent Chat")
            val chatInterface = info.plateaukao.einkbro.browser.ChatWebInterface(
                scope = scope,
                engine = engine,
                webContent = "",
                webTitle = snapshot?.title.orEmpty(),
                webUrl = snapshot?.url.orEmpty(),
                onOpenNewTab = { url -> browserViewModel.newTab(url) },
                agentMode = true,
                agentSnapshot = snapshot,
                agentTtsViewModel = ttsViewModel,
                agentActiveEngineProvider = { browserViewModel.currentEngine },
            )
            browserViewModel.attachChatInterface(engine.album.id, chatInterface)
            chatInterface.loadChatPage(initialPrompt = prompt)
        }
    }

    fun translateWithMode(mode: TranslationMode) {
        val currentHelper = helper ?: return
        val bridge = browserViewModel.translationBridge
        when (mode) {
            TranslationMode.TRANSLATE_BY_PARAGRAPH -> {
                bridge.translateApi = TRANSLATE_API.GOOGLE
                currentHelper.translateByParagraph()
            }

            TranslationMode.OPENAI_BY_PARAGRAPH -> {
                bridge.translateApi = TRANSLATE_API.OPENAI
                currentHelper.translateByParagraph()
            }

            TranslationMode.GEMINI_BY_PARAGRAPH -> {
                bridge.translateApi = TRANSLATE_API.GEMINI
                currentHelper.translateByParagraph()
            }

            TranslationMode.OPENAI_IN_PLACE -> {
                bridge.translateApi = TRANSLATE_API.OPENAI
                currentHelper.translateInPlaceReplace()
            }

            TranslationMode.GEMINI_IN_PLACE -> {
                bridge.translateApi = TRANSLATE_API.GEMINI
                currentHelper.translateInPlaceReplace()
            }

            TranslationMode.GOOGLE_URL -> browserViewModel.newTab(
                "https://translate.google.com/translate?sl=auto" +
                    "&tl=${config.translation.translationLanguage.value}" +
                    "&u=${browserViewModel.currentUrl.value}"
            )

            TranslationMode.GOOGLE_IN_PLACE -> currentHelper.addGoogleTranslation()
        }
    }

    fun comingSoon(feature: String, phase: Char) =
        EBToast.show(AppServices.context, "$feature: coming in Phase $phase")

    /** Paragraph-translate with an explicit provider (language-config dialog). */
    fun translateByParagraphWith(api: TRANSLATE_API) {
        browserViewModel.translationBridge.translateApi = api
        browserViewModel.currentHelper?.translateByParagraph()
    }

    /** Extracts page headings and opens the TOC dialog (empty page → toast). */
    fun showToc() {
        val currentEngine = browserViewModel.currentEngine ?: return
        currentEngine.evaluateJavascript(Assets.get("get_toc.js")) { result ->
            val entries = result?.let {
                runCatching { tocJson.decodeFromString<List<TocEntry>>(it) }.getOrNull()
            }.orEmpty()
            if (entries.isEmpty()) {
                EBToast.show(AppServices.context, "No headings found on this page")
            } else {
                tocItems = entries.mapIndexed { index, entry ->
                    TocItem(
                        title = "    ".repeat((entry.level - 1).coerceIn(0, 3)) + entry.text,
                        originalIndex = index,
                    )
                }
            }
        }
    }

    /**
     * The single [BrowserAction] dispatcher — every input surface (toolbar
     * click/long-press, menu, context menus, later gestures) funnels here.
     * Mirrors Android's BrowserActivity.dispatch().
     */
    fun handleBrowserAction(action: BrowserAction) {
        val currentEngine = browserViewModel.currentEngine
        val currentHelper = browserViewModel.currentHelper
        when (action) {
            BrowserAction.Noop -> Unit

            // Tab management
            BrowserAction.NewATab -> when (config.tab.newTabBehavior) {
                info.plateaukao.einkbro.preference.NewTabBehavior.START_INPUT -> {
                    browserViewModel.newTab("", title = "New tab")
                    showUrlInput = true
                }
                info.plateaukao.einkbro.preference.NewTabBehavior.SHOW_HOME ->
                    browserViewModel.newTab(
                        config.favoriteUrl.ifBlank { BrowserViewModel.DEFAULT_HOME }
                    )
                info.plateaukao.einkbro.preference.NewTabBehavior.SHOW_RECENT_BOOKMARKS -> {
                    // Android BookmarkRenderer.loadRecentlyUsedBookmarks: the new
                    // tab shows an HTML card list of recently used bookmarks.
                    val html = recentBookmarksHtml(config)
                    if (html.isNotBlank()) {
                        browserViewModel.newTab("", title = "Recently used bookmarks")
                        browserViewModel.currentEngine?.loadHtml(html)
                    } else {
                        browserViewModel.newTab("", title = "New tab")
                        showBookmarks = true
                    }
                }
            }
            BrowserAction.DuplicateTab ->
                browserViewModel.newTab(browserViewModel.currentUrl.value)
            BrowserAction.RemoveAlbum ->
                browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) } ?: Unit
            BrowserAction.GotoLeftTab -> browserViewModel.gotoLeftTab()
            BrowserAction.GotoRightTab -> browserViewModel.gotoRightTab()
            is BrowserAction.AddNewTab -> browserViewModel.newTab(action.url)
            is BrowserAction.UpdateAlbum ->
                action.url?.takeIf { it.isNotBlank() }?.let { currentEngine?.loadUrl(it) } ?: Unit

            // Navigation
            BrowserAction.GoForward ->
                if (currentEngine?.canGoForward() == true) currentEngine.goForward()
                else EBToast.show(AppServices.context, "Can't go forward")
            BrowserAction.HandleBackKey -> when {
                showOverview -> showOverview = false
                // "Toolbar first": when hidden by scrolling, Back restores the
                // toolbar before navigating (Android BrowserActivity L655).
                config.ui.showToolbarFirst && toolbarHiddenByScroll ->
                    toolbarHiddenByScroll = false
                currentEngine?.canGoBack() == true -> currentEngine.goBack()
                config.tab.closeTabWhenNoMoreBackHistory ->
                    browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) } ?: Unit
                else -> EBToast.show(AppServices.context, "No previous page")
            }
            BrowserAction.RefreshAction ->
                if (browserViewModel.progress.value < 1f) currentEngine?.stopLoading()
                else currentEngine?.reload()
            BrowserAction.JumpToTop -> currentHelper?.jumpToTop() ?: currentEngine?.jumpToTop()
            BrowserAction.JumpToBottom ->
                currentHelper?.jumpToBottom() ?: currentEngine?.jumpToBottom()
            BrowserAction.PageUp -> currentHelper?.pageUp() ?: currentEngine?.pageUp()
            BrowserAction.PageDown -> currentHelper?.pageDown() ?: currentEngine?.pageDown()
            BrowserAction.SendPageUpKey -> currentHelper?.pageUp() ?: currentEngine?.pageUp()
            BrowserAction.SendPageDownKey ->
                currentHelper?.pageDown() ?: currentEngine?.pageDown()
            BrowserAction.SendLeftKey -> comingSoon("Arrow-key paging", 'F')
            BrowserAction.SendRightKey -> comingSoon("Arrow-key paging", 'F')

            // Content
            BrowserAction.ToggleReaderMode -> currentHelper?.toggleReaderMode() ?: Unit
            BrowserAction.ToggleVerticalRead -> currentHelper?.toggleVerticalRead() ?: Unit
            BrowserAction.IncreaseFontSize -> {
                if (currentHelper?.isReaderModeOn == true) {
                    config.display.readerFontSize =
                        (config.display.readerFontSize + 20).coerceAtMost(300)
                } else {
                    config.display.fontSize = (config.display.fontSize + 20).coerceAtMost(300)
                }
                currentHelper?.updateCssStyle()
            }
            BrowserAction.DecreaseFontSize -> {
                if (currentHelper?.isReaderModeOn == true) {
                    config.display.readerFontSize =
                        (config.display.readerFontSize - 20).coerceAtLeast(50)
                } else {
                    config.display.fontSize = (config.display.fontSize - 20).coerceAtLeast(50)
                }
                currentHelper?.updateCssStyle()
            }
            BrowserAction.ShowFontSizeChangeDialog -> {
                fontDialogForReader = currentHelper?.isReaderModeOn == true
                showFontDialog = true
            }
            BrowserAction.ShowFontBoldnessDialog -> showBoldnessDialog = true
            BrowserAction.ShowReaderSettingsDialog -> showReaderSettings = true
            BrowserAction.InvertColors -> currentHelper?.toggleInvertColor() ?: Unit

            // View state
            BrowserAction.ShowOverview -> {
                overviewShowsHistory = false
                showOverview = !showOverview
            }
            BrowserAction.ToggleFullscreen -> {
                isFullscreen = !isFullscreen
            }
            is BrowserAction.ToggleSplitScreen -> browserViewModel.toggleSplitScreen(action.url)

            // Translation. Click runs the selected mode directly (Android's
            // toolbar click → translate(getTranslationMode(url))); the config
            // dropdown is reachable via long-press (ShowTranslationConfigDialog).
            BrowserAction.ShowTranslation -> {
                if (currentHelper?.isTranslateByParagraph == true) {
                    currentHelper.clearTranslationElements()
                    EBToast.show(AppServices.context, "Translation cleared")
                } else {
                    translateWithMode(
                        config.getTranslationMode(browserViewModel.currentUrl.value)
                    )
                }
            }
            is BrowserAction.ShowTranslationConfigDialog -> showTranslationConfig = true
            is BrowserAction.Translate -> translateWithMode(action.mode)
            is BrowserAction.ConfigureTranslationLanguage -> languageConfigApi = action.api

            // TTS
            BrowserAction.HandleTtsButton -> {
                if (!ttsViewModel.isReading()) {
                    currentHelper?.let { helper ->
                        scope.launch {
                            val text = helper.getRawTextWithCaption()
                            if (text.isNotBlank()) {
                                ttsViewModel.readArticle(text, browserViewModel.currentTitle.value)
                            }
                        }
                    }
                }
                showTtsDialog = true
            }
            BrowserAction.ShowTtsSettingsDialog -> showTtsDialog = true

            // Bookmarks / History
            BrowserAction.OpenBookmarkPage -> showBookmarks = true
            is BrowserAction.OpenHistoryPage -> {
                overviewShowsHistory = true
                showOverview = true
            }
            is BrowserAction.SaveBookmark -> {
                val url = action.url ?: browserViewModel.currentUrl.value
                val title = (action.title ?: browserViewModel.currentTitle.value).ifBlank { url }
                if (url.isNotBlank()) {
                    scope.launch {
                        AppServices.bookmarkManager.insert(Bookmark(title = title, url = url))
                        EBToast.show(AppServices.context, "Bookmark saved")
                    }
                }
                Unit
            }

            // Search / remote (Phases E and J)
            BrowserAction.ShowSearchPanel -> {
                searchResultInfo = ""
                showSearchBar = true
            }
            BrowserAction.ToggleTextSearch -> comingSoon("Remote text search", 'J')
            BrowserAction.ToggleReceiveTextSearch -> comingSoon("Remote text search", 'J')

            // Share
            BrowserAction.ShareLink -> PlatformActions.share(browserViewModel.currentUrl.value)
            BrowserAction.ShareLinkToLastTarget ->
                EBToast.show(AppServices.context, "iOS share sheet has no last-target shortcut")
            BrowserAction.ShareLinkLongPress -> when (config.browser.shareLongPressAction) {
                ShareLongPressAction.COPY_LINK -> {
                    PlatformActions.copyToClipboard(
                        stripUrlQuery(browserViewModel.currentUrl.value)
                    )
                    EBToast.show(AppServices.context, "Link copied")
                }
                ShareLongPressAction.LAST_SHARE_TARGET ->
                    EBToast.show(AppServices.context, "iOS share sheet has no last-target shortcut")
            }
            is BrowserAction.SendToRemote -> {
                info.plateaukao.einkbro.util.LanShare.startBroadcast(
                    action.text, times = 10,
                    onError = { EBToast.show(AppServices.context, it) },
                )
                AppServices.dialogManager.showOkCancelDialog(
                    title = "Send link",
                    message = "Broadcasting the link on the local network…",
                    okAction = { info.plateaukao.einkbro.util.LanShare.stop() },
                    showNegativeButton = false,
                )
            }
            BrowserAction.AddToInstapaper ->
                if (browserViewModel.hasInstapaperCredentials()) browserViewModel.addToInstapaper()
                else showInstapaperConfig = true
            BrowserAction.ConfigureInstapaper -> showInstapaperConfig = true
            BrowserAction.ToggleReceiveLink -> {
                info.plateaukao.einkbro.util.LanShare.startReceiving(
                    onError = { EBToast.show(AppServices.context, it) },
                    onMessage = { message ->
                        if (message.startsWith("http")) {
                            info.plateaukao.einkbro.util.LanShare.stop()
                            // Close the "waiting" dialog before opening the link.
                            info.plateaukao.einkbro.view.dialog.DialogManager.pendingOkCancel.value =
                                null
                            browserViewModel.newTab(message, activate = true)
                        }
                    },
                )
                AppServices.dialogManager.showOkCancelDialog(
                    title = "Receive link",
                    message = "Waiting for a link from the local network…",
                    okAction = { info.plateaukao.einkbro.util.LanShare.stop() },
                    showNegativeButton = false,
                )
            }

            // Touch config
            BrowserAction.ToggleTouchTurnPage, BrowserAction.ToggleTouchPagination -> {
                config.touch.enableTouchTurn = !touchPagingEnabled
                touchPagingEnabled = !touchPagingEnabled
                EBToast.show(
                    AppServices.context,
                    if (touchPagingEnabled) "Touch paging on" else "Touch paging off"
                )
            }
            BrowserAction.ToggleSwitchTouchAreaAction -> {
                config.touch.switchTouchAreaAction = !config.touch.switchTouchAreaAction
                EBToast.show(
                    AppServices.context,
                    if (config.touch.switchTouchAreaAction) "Touch areas switched"
                    else "Touch areas restored"
                )
            }
            BrowserAction.ShowTouchAreaDialog -> showTouchAreaDialog = true

            // AI
            BrowserAction.SummarizeContent -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    currentHelper?.let { helper ->
                        scope.launch {
                            val text = helper.getRawTextWithCaption()
                            if (text.isNotBlank()) {
                                translationViewModel.url = browserViewModel.currentUrl.value
                                translationViewModel.pageTitle = browserViewModel.currentTitle.value
                                translationViewModel.setupTextSummary(text)
                                translateDialogWholePage = true
                                showTranslateDialog = true
                            }
                        }
                    } ?: Unit
                }
            }
            is BrowserAction.ChatWithWeb -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    // Both variants open a native chat tab (Android: new tab vs
                    // split screen; the split-pane chat variant is deferred).
                    val runAction = action.runWithAction
                    val presetContent = action.content
                    currentHelper?.let { helper ->
                        scope.launch {
                            val content = presetContent ?: helper.getRawTextWithCaption()
                            if (content.isNotBlank()) {
                                openChatWithWebTab(content, runAction)
                            }
                        }
                    } ?: Unit
                }
            }
            BrowserAction.ShowPageAiActionMenu -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    showPageAiActions = true
                }
            }
            BrowserAction.ShowTaskMenu -> showTaskMenu = true
            is BrowserAction.RunTask -> runTaskById(action.taskId)
            is BrowserAction.RunCustomTask -> runCustomTask(action.prompt)

            // File
            BrowserAction.ShowEpubDialog -> showEpubDialog = true
            BrowserAction.SavePageForLater, BrowserAction.SaveWebArchive -> {
                EBToast.show(AppServices.context, "Saving page…")
                browserViewModel.saveWebArchive { ok ->
                    EBToast.show(
                        AppServices.context,
                        if (ok) "Saved for offline reading" else "Couldn't save page",
                    )
                }
            }
            BrowserAction.ShowSavedPages -> showSavedPages = true
            BrowserAction.SavePdf -> {
                EBToast.show(AppServices.context, "Saving PDF…")
                browserViewModel.saveAsPdf { ok ->
                    if (!ok) EBToast.show(AppServices.context, "Couldn't save PDF")
                }
            }

            // Dialog / UI
            BrowserAction.FocusOnInput -> showUrlInput = true
            BrowserAction.ShowMenuDialog -> showMenu = true
            BrowserAction.ShowFastToggleDialog -> showFastToggle = true
            BrowserAction.ShowTocDialog -> showToc()
            BrowserAction.RotateScreen ->
                EBToast.show(AppServices.context, "Rotate your device — iOS controls orientation")
            BrowserAction.ToggleAudioOnlyMode -> currentHelper?.toggleAudioOnly() ?: Unit
            BrowserAction.ShowSiteSettingsDialog -> showSiteSettings = true
            BrowserAction.ShowUserScriptCommands -> {
                // Short-tap parity: list this page's registered menu commands, or
                // fall back to the manager when the page registered none.
                val commands = browserViewModel.userScriptMenuCommands
                if (commands.isEmpty()) showUserScripts = true
                else userScriptCommands = commands
            }

            // iOS host additions
            BrowserAction.ToggleBoldFont -> {
                config.display.boldFontStyle = !config.display.boldFontStyle
                currentHelper?.updateCssStyle()
            }
            BrowserAction.ToggleBlackFont -> {
                config.display.blackFontStyle = !config.display.blackFontStyle
                currentHelper?.updateCssStyle()
            }
            BrowserAction.ToggleWhiteBackground -> {
                config.toggleWhiteBackground(browserViewModel.currentUrl.value)
                currentHelper?.updateCssStyle()
            }
            BrowserAction.ToggleDesktopMode -> {
                config.browser.desktop = !config.browser.desktop
                browserViewModel.reapplyWebConfig()
                currentEngine?.reload()
                EBToast.show(
                    AppServices.context,
                    if (config.browser.desktop) "Desktop mode on" else "Desktop mode off"
                )
            }
            BrowserAction.ToggleIncognitoMode -> {
                config.isIncognitoMode = !config.isIncognitoMode
                EBToast.show(
                    AppServices.context,
                    if (config.isIncognitoMode) "Incognito on for new tabs"
                    else "Incognito off"
                )
            }
            BrowserAction.ShowToolbarConfigDialog -> showToolbarConfig = true
            BrowserAction.OpenSettings -> {
                settingsInitialRoute = info.plateaukao.einkbro.activity.SettingRoute.Main
                showSettings = true
            }
            BrowserAction.ShowHighlights -> showHighlights = true
            BrowserAction.OpenUserScriptManager -> showUserScripts = true
        }
    }

    // Touch/FAB gestures dispatch a bound BrowserAction. Page-turn actions flip
    // in vertical-rl mode so the zones still advance in reading order.
    fun runTouchGesture(action: BrowserAction) {
        val vertical = browserViewModel.currentHelper?.isVerticalRead == true
        val effective = if (vertical) {
            when (action) {
                BrowserAction.PageUp -> BrowserAction.PageDown
                BrowserAction.PageDown -> BrowserAction.PageUp
                else -> action
            }
        } else action
        handleBrowserAction(effective)
    }

    val toolbarActionHandler = ToolbarActionHandler { handleBrowserAction(it) }
    val menuActionHandler = MenuActionHandler(
        dispatch = { handleBrowserAction(it) },
        currentUrl = { browserViewModel.currentUrl.value },
    )

    // Auto-hide toolbar on scroll (shouldHideToolbar): hide after a clear
    // downward scroll away from the top; restore on scroll-up or at the top.
    // Scrolls while the keyboard is up are WKWebView revealing the caret, not
    // user intent — reacting to them resizes the webview, which re-triggers
    // the caret-reveal scroll: an endless show/hide oscillation (visible as
    // the page shaking).
    val imeVisible by rememberUpdatedState(
        WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    )
    LaunchedEffect(engine) {
        engine?.setScrollChangeHandler { dy, y ->
            when {
                imeVisible -> Unit
                !config.ui.shouldHideToolbar -> toolbarHiddenByScroll = false
                dy > 12 && y > 100 -> toolbarHiddenByScroll = true
                dy < -12 || y <= 0 -> toolbarHiddenByScroll = false
            }
        }
    }

    // Two-finger swipe paging (parity Phase F multitouch). Rides on the engine's
    // native gesture recognizers — a Compose overlay can't catch two-finger
    // gestures over the WKWebView interop. Bindings read live from config.
    LaunchedEffect(engine) {
        engine?.setMultitouchSwipeHandler { dir ->
            if (config.touch.isMultitouchEnabled) {
                val action = when (dir) {
                    MultitouchDirection.UP -> config.touch.multitouchUp
                    MultitouchDirection.DOWN -> config.touch.multitouchDown
                    MultitouchDirection.LEFT -> config.touch.multitouchLeft
                    MultitouchDirection.RIGHT -> config.touch.multitouchRight
                }
                if (action != BrowserAction.Noop) runTouchGesture(action)
            }
        }
    }

    // Auto-translate a site the user marked (parity Phase M): fires the per-site
    // translation mode each time a page there finishes loading. GOOGLE_URL is
    // skipped in auto mode — it opens a new tab, so it would loop on redirect.
    LaunchedEffect(browserViewModel.pageFinishedTick.value) {
        val url = browserViewModel.lastFinishedUrl
        if (url.isNotBlank() && config.shouldTranslateSite(url)) {
            when (val mode = config.getTranslationMode(url)) {
                TranslationMode.GOOGLE_URL -> Unit
                else -> translateWithMode(mode)
            }
        }
    }

    fun handleContextMenuItem(item: ContextMenuItemType, url: String) {
        when (item) {
            ContextMenuItemType.NewTabForeground -> browserViewModel.newTab(url)
            ContextMenuItemType.NewTabBackground ->
                browserViewModel.newTab(url, activate = false)

            ContextMenuItemType.ShareLink -> PlatformActions.share(url)
            ContextMenuItemType.OpenWith -> PlatformActions.openUrl(url)
            ContextMenuItemType.SaveBookmark ->
                handleBrowserAction(BrowserAction.SaveBookmark(url = url, title = url))
            ContextMenuItemType.GotoLink -> browserViewModel.currentEngine?.loadUrl(url)
            ContextMenuItemType.SplitScreen -> browserViewModel.toggleSplitScreen(url)
            ContextMenuItemType.Summarize -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    EBToast.show(AppServices.context, "Fetching link…")
                    scope.launch {
                        val text =
                            info.plateaukao.einkbro.data.remote.PageContentFetcher.fetchText(url)
                        if (text.isNullOrBlank()) {
                            EBToast.show(AppServices.context, "Could not fetch link content")
                            return@launch
                        }
                        translationViewModel.url = url
                        translationViewModel.pageTitle = url
                        translationViewModel.setupTextSummary(text)
                        translateDialogWholePage = true
                        showTranslateDialog = true
                    }
                }
            }
            ContextMenuItemType.Tts -> {
                EBToast.show(AppServices.context, "Fetching link…")
                scope.launch {
                    val text =
                        info.plateaukao.einkbro.data.remote.PageContentFetcher.fetchText(url)
                    if (text.isNullOrBlank()) {
                        EBToast.show(AppServices.context, "Could not fetch link content")
                        return@launch
                    }
                    ttsViewModel.readArticle(text, url)
                }
            }
            ContextMenuItemType.SaveAs -> browserViewModel.currentEngine?.startDownload(url)
            ContextMenuItemType.SelectText ->
                EBToast.show(AppServices.context, "Long-press the text itself to select on iOS")
            else -> Unit
        }
    }

    fun handleContextMenuLongClick(item: ContextMenuItemType, url: String) {
        when (item) {
            ContextMenuItemType.ShareLink -> {
                PlatformActions.copyToClipboard(stripUrlQuery(url))
                EBToast.show(AppServices.context, "Link copied")
            }
            else -> Unit
        }
    }

    // Window origin of the web pane, kept for anchoring the link context menu.
    var webPaneOffset by remember { mutableStateOf(Offset.Zero) }

    // The bottom safe-area inset is not reserved at the root: the webview may
    // run under the home indicator (fullscreen / toolbar hidden by scroll).
    // Bottom chrome either occupies the home-indicator band with the system
    // gesture deferred (edgeToEdgeToolbar, the default) or pads itself above
    // it Safari-style — background painting to the edge, tap targets lifted
    // out of the band (pref off, or iOS 15 without a deferral lever).
    val rootInsets = when {
        // Fullscreen additionally runs under the status bar.
        isFullscreen || statusBarSuppressed ->
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
        else ->
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(rootInsets)) {
        // The main web pane and all its overlays, rendered into whatever slot the
        // split layout gives it (parity Phase G wraps it beside the second pane).
        val renderMainPane: @Composable (Modifier) -> Unit = { paneModifier ->
        BoxWithConstraints(
            // The link context menu is hosted in a window-spanning dialog, so it
            // needs this pane's window origin to place the JS-reported (pane-
            // relative) long-press point.
            paneModifier.onGloballyPositioned { webPaneOffset = it.positionInWindow() }
        ) {
            // AI chat tabs are ordinary web tabs rendering chat.html (Android
            // parity, d0dd4db's native pane retired) — no special mount path.
            val isAiTab = browserViewModel.currentAlbum?.isAIPage == true
            if (engine != null) {
                // Key by tab id so UIKitView re-embeds the current tab's
                // WKWebView when the active tab changes (its factory runs once).
                key(browserViewModel.currentAlbum?.id) {
                    WebViewHost(engine, Modifier.fillMaxSize())
                }
            }

            // Touch-area page-turn zones (parity Phase F). Hidden while the URL
            // input is up when hideTouchAreaWhenInput is set. A chat tab owns
            // its whole pane — no page-turn overlays over it.
            if (!isAiTab && touchPagingEnabled &&
                !(config.touch.hideTouchAreaWhenInput && showUrlInput)
            ) {
                TouchAreaZones(onGesture = { runTouchGesture(it) })
            }

            // Nav-gesture FAB (parity Phase F, enableNavButtonGesture). Android
            // shows it only while the toolbar is hidden (FullscreenDelegate
            // show()/hide() on toggleFullscreen), never alongside the toolbar.
            if (!isAiTab && config.touch.enableNavButtonGesture &&
                (isFullscreen || toolbarHiddenByScroll)
            ) {
                NavGestureFab(onGesture = { runTouchGesture(it) })
            }

            // Text-selection action menu, anchored just below the selection.
            // Hidden while a link context menu owns the interaction.
            browserViewModel.selectionInfo.value
                ?.takeIf { browserViewModel.contextMenuLink.value == null }
                ?.let { selection ->
                val menuWidthDp = 280f
                val estMenuHeightDp = 160f
                val x = selection.left
                    .coerceIn(0f, (maxWidth.value - menuWidthDp).coerceAtLeast(0f))
                val below = selection.bottom + 8f
                val y = if (below + estMenuHeightDp <= maxHeight.value) below
                else (selection.top - estMenuHeightDp - 8f).coerceAtLeast(0f)
                val selectionMenus = remember(selection.text) {
                    mutableStateOf(
                        listOf(
                            MenuInfo("Copy", imageVector = Icons.Outlined.ContentCopy, action = {
                                PlatformActions.copyToClipboard(selection.text)
                            }),
                            MenuInfo(
                                "Highlight",
                                // ic_highlight_color is a white-filled/tinted
                                // vector that renders invisible untinted; use
                                // the theme-tinted material icon instead.
                                imageVector = Icons.Outlined.Highlight,
                                action = { browserViewModel.highlightCurrentSelection() },
                            ),
                            MenuInfo("Translate", imageVector = Icons.Outlined.Translate, action = {
                                translationViewModel.updateInputMessage(selection.text)
                                translationViewModel.updateMessageWithContext(selection.text)
                                if (translationViewModel.translateMethod.value == TRANSLATE_API.LLM &&
                                    !translationViewModel.hasOpenAiApiKey()
                                ) {
                                    translationViewModel.updateTranslateMethod(TRANSLATE_API.GOOGLE)
                                }
                                translateDialogWholePage = false
                                showTranslateDialog = true
                            }),
                            MenuInfo("Read", imageVector = Icons.Outlined.RecordVoiceOver, action = {
                                ttsViewModel.readArticle(selection.text)
                            }),
                            MenuInfo("Search", imageVector = Icons.Outlined.Search, action = {
                                browserViewModel.searchInNewTab(selection.text)
                            }),
                            MenuInfo("Share", imageVector = Icons.Outlined.Share, action = {
                                PlatformActions.share(selection.text)
                            }),
                        ) + config.ai.gptActionList
                            .filter { it.scope == GptActionScope.TextSelection }
                            .map { gptAction ->
                            // Android's ActionModeMenuViewModel appends the GPT
                            // actions to the selection menu.
                            MenuInfo(
                                gptAction.name,
                                drawable = Res.drawable.ic_chat_gpt,
                                action = {
                                    translationViewModel.url =
                                        browserViewModel.currentUrl.value
                                    translationViewModel.updateInputMessage(selection.text)
                                    translationViewModel.updateMessageWithContext(selection.text)
                                    translationViewModel.setupGptAction(gptAction)
                                    translateDialogWholePage = false
                                    showTranslateDialog = true
                                },
                            )
                        }
                    )
                }
                Box(Modifier.align(Alignment.TopStart).offset(x.dp, y.dp)) {
                    ActionModeMenu(
                        menus = selectionMenus,
                        showIcons = true,
                        onClicked = { browserViewModel.clearSelection() },
                    )
                }
            }

            if (showUrlInput) {
                val text = remember {
                    mutableStateOf(
                        TextFieldValue(
                            browserViewModel.currentUrl.value,
                            selection = TextRange(0, browserViewModel.currentUrl.value.length),
                        )
                    )
                }
                val recordsState = remember(browserViewModel.records.value.size) {
                    mutableStateOf(browserViewModel.records.value)
                }
                val urlFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { urlFocusRequester.requestFocus() }
                // Android SearchSuggestionViewModel.updateSuggestions: filter
                // local history/bookmarks by the query and, when enabled, put up
                // to 4 engine suggestions ahead of them (debounced per keystroke).
                var suggestionQuery by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    snapshotFlow { suggestionQuery }.collectLatest { query ->
                        val all = browserViewModel.records.value
                        if (query.isEmpty()) {
                            recordsState.value = all
                            return@collectLatest
                        }
                        val filtered = all.filter {
                            it.title?.contains(query, ignoreCase = true) == true ||
                                it.url.contains(query, ignoreCase = true)
                        }
                        if ((query.length <= 1 && filtered.isNotEmpty()) ||
                            !config.browser.enableSearchSuggestion
                        ) {
                            recordsState.value = filtered
                            return@collectLatest
                        }
                        recordsState.value = filtered
                        delay(200)
                        val fromEngine = info.plateaukao.einkbro.search.suggestion
                            .SearchSuggestionFetcher
                            .fetch(config.browser.searchEngine, query)
                            .take(4)
                            .map {
                                Record(
                                    title = it, url = it, time = -1,
                                    type = info.plateaukao.einkbro.database.RecordType.Suggestion,
                                )
                            }
                        recordsState.value = fromEngine + filtered
                    }
                }
                // Transparent overlay, mirroring Android's inputUrl ComposeView:
                // the Column paints nothing, so only the opaque input row and
                // suggestion list show and the page stays visible behind the
                // empty area (tapping it dismisses). imePadding: with
                // onFocusBehavior=DoNothing the scene is not panned for the
                // keyboard, so the reversed (bottom-toolbar) input row must lift
                // itself above the ime.
                Box(Modifier.fillMaxSize().imePadding()) {
                    AutoCompleteTextField(
                        focusRequester = urlFocusRequester,
                        // Behavior pref: surface bookmarks (with favicons) in the
                        // input bar's suggestion list.
                        bookmarkManager = if (config.browser.showBookmarksInInputBar)
                            AppServices.bookmarkManager else null,
                        // Android InputBarDelegate: the text field sits at the
                        // toolbar's edge — bottom toolbar puts the input bottom.
                        shouldReverse = !config.ui.isToolbarOnTop,
                        showHistoryThumbnailGrid = config.ui.showHistoryThumbnailGrid,
                        text = text,
                        recordList = recordsState,
                        onTextSubmit = {
                            browserViewModel.loadUrlOrSearch(it); showUrlInput = false
                        },
                        onTextChange = { suggestionQuery = it },
                        onPasteClick = {},
                        closeAction = { showUrlInput = false },
                        onRecordClick = {
                            browserViewModel.loadUrlOrSearch(it.url); showUrlInput = false
                        },
                    )
                }
            }
        }
        }

        // Toolbar + custom statusbar as reusable slots so toolbarPosition (Top vs
        // Bottom) and statusbarPosition can place them around the pane (parity
        // Phase N). Left/Right toolbar falls back to bottom for now.
        val toolbarAtTop = config.ui.isToolbarOnTop
        val renderToolbar: @Composable () -> Unit = {
            // Hide the toolbar while the URL input is up (Android sets appBar
            // INVISIBLE in InputBarDelegate). The pane then fills the freed
            // space, so the bottom-anchored input row lands flush at the edge —
            // over where the toolbar was — and its taps can't reach the buttons.
            if (!isFullscreen && !toolbarHiddenByScroll && !showUrlInput) {
                // Bottom toolbar (and the vertical rail, whose lowest icons also
                // reach the edge) sits flush in the home-indicator band while
                // the system gesture is deferred (edgeToEdgeToolbar); otherwise
                // it is lifted above the band, the background filling the gap
                // down to the physical edge.
                val bottomInset =
                    if ((!toolbarAtTop || config.ui.isVerticalToolbar) &&
                        !edgeToEdgeToolbar
                    ) {
                        Modifier
                            .background(MaterialTheme.colors.background)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                            )
                    } else {
                        Modifier
                    }
                Box(bottomInset) {
                ComposedToolbar(
                    isVertical = config.ui.isVerticalToolbar,
                    showTabs = showTabStrip && !config.ui.isVerticalToolbar,
                    toolbarActionInfos = remember(toolbarRefreshTick) {
                        config.ui.toolbarActions.map { ToolbarActionInfo(it, false) }
                    },
                    title = browserViewModel.currentTitle.value
                        .ifBlank { browserViewModel.currentUrl.value },
                    tabCount = browserViewModel.albums.value.size.toString(),
                    pageInfo = "",
                    isIncognito = browserViewModel.currentAlbum?.incognito == true,
                    onIconClick = { toolbarActionHandler.handleClick(it) },
                    onIconLongClick = { toolbarActionHandler.handleLongClick(it) },
                    albumList = browserViewModel.albums,
                    albumFocusIndex = browserViewModel.focusIndex,
                    onAlbumClick = { browserViewModel.showOrJumpToTop(it) },
                    onAlbumLongClick = { browserViewModel.closeTab(it) },
                )
                }
            }
        }
        // Android parity (StatusbarViewController): the custom statusbar is a
        // stand-in info strip shown ONLY while the toolbar is hidden (fullscreen
        // toggle or hide-on-scroll) and the statusbar setting is enabled; it is
        // never rendered alongside a visible toolbar.
        val statusbarVisible = config.ui.statusbarEnabled &&
            (isFullscreen || toolbarHiddenByScroll)
        val renderStatusbar: @Composable () -> Unit = {
            info.plateaukao.einkbro.view.statusbar.Statusbar(
                items = config.ui.statusbarItems,
                pageInfo = "",
            )
        }
        if (toolbarAtTop && !config.ui.isVerticalToolbar) renderToolbar()
        if (statusbarVisible &&
            config.ui.statusbarPosition ==
            info.plateaukao.einkbro.view.statusbar.StatusbarPosition.Top
        ) {
            // Deliberately NOT safe-area inset: the info bar has no tap targets,
            // and fullscreen content should be full-bleed to the physical edges.
            renderStatusbar()
        }

        // Split screen (parity Phase G): the second pane sits beside the main one,
        // horizontally (side-by-side) or vertically (stacked) per orientation.
        val renderPaneArea: @Composable ColumnScope.() -> Unit = {
            val splitAlbumG = browserViewModel.splitAlbum.value
            if (splitAlbumG == null) {
                renderMainPane(Modifier.weight(1f).fillMaxWidth())
            } else if (browserViewModel.splitOrientation.value ==
                info.plateaukao.einkbro.view.Orientation.Vertical
            ) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    renderMainPane(Modifier.weight(1f).fillMaxWidth())
                    SplitPane(Modifier.weight(1f).fillMaxWidth(), browserViewModel, statusBarSuppressed)
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    renderMainPane(Modifier.weight(1f).fillMaxHeight())
                    SplitPane(Modifier.weight(1f).fillMaxHeight(), browserViewModel, statusBarSuppressed)
                }
            }
        }
        if (config.ui.isVerticalToolbar) {
            // Left/Right toolbarPosition: the toolbar is a vertical rail beside
            // the page (Android isVerticalToolbar; tab strip is suppressed).
            val onLeft = config.ui.toolbarPosition ==
                info.plateaukao.einkbro.preference.ToolbarPosition.Left
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (onLeft) renderToolbar()
                Column(Modifier.weight(1f).fillMaxHeight()) { renderPaneArea() }
                if (!onLeft) renderToolbar()
            }
        } else {
            renderPaneArea()
        }

        if (progress < 1f) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colors.onBackground,
            )
        }

        // Find-on-page bar (parity Phase E) sits just above the toolbar.
        if (showSearchBar) {
            fun applyFind(count: Int, index: Int) {
                searchResultInfo = if (count == 0) "0/0" else "$index/$count"
            }
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { searchFocus.requestFocus() }
            Box(Modifier.imePadding()) {
            ComposedSearchBar(
                focusRequester = searchFocus,
                onTextChanged = { q ->
                    helper?.findOnPage("find", q) { c, i -> applyFind(c, i) }
                },
                onDownClick = { helper?.findOnPage("next") { c, i -> applyFind(c, i) } },
                onUpClick = { helper?.findOnPage("prev") { c, i -> applyFind(c, i) } },
                onCloseClick = {
                    helper?.findOnPage("clear")
                    searchResultInfo = ""
                    showSearchBar = false
                },
                resultInfo = searchResultInfo,
            )
            }
        }

        // Fullscreen (parity Phase D) hides the toolbar; a small exit chip
        // brings it back (iOS has no back key to restore it like Android).
        if (statusbarVisible &&
            config.ui.statusbarPosition ==
            info.plateaukao.einkbro.view.statusbar.StatusbarPosition.Bottom
        ) {
            // Deliberately NOT safe-area inset (unlike the toolbar): the info
            // bar has no tap targets, so it sits flush at the physical bottom
            // with the home indicator overlaying it.
            renderStatusbar()
        }
        if (!toolbarAtTop && !config.ui.isVerticalToolbar) renderToolbar()
    }

    if (showOverview) {
        // Transparent window-spanning overlay like Android's
        // OverviewDialogController (layout_overview is constrained to all four
        // parent edges): the panel's button bar draws OVER the main toolbar
        // rather than stacking above it. The page stays visible behind, and
        // tapping the empty area closes it.
        val overviewBottomInset =
            if (!config.ui.isToolbarOnTop && !edgeToEdgeToolbar) {
                // Same lift the toolbar applies when the home-indicator band
                // is not tap-safe (no gesture deferral).
                Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                )
            } else {
                Modifier
            }
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(rootInsets)
                .then(overviewBottomInset)
        ) {
            HistoryAndTabs(
                bookmarkManager = AppServices.bookmarkManager,
                isHistoryOpen = overviewShowsHistory,
                // Anchor the panel at the toolbar's edge, like Android's
                // OverviewDialogController (bar at bottom unless on top).
                shouldReverseHistory = !config.ui.isToolbarOnTop,
                albumList = browserViewModel.albums,
                albumFocusIndex = browserViewModel.focusIndex,
                onTabIconClick = { overviewShowsHistory = false },
                onTabClick = { browserViewModel.showOrJumpToTop(it); showOverview = false },
                onTabLongClick = { browserViewModel.closeTab(it) },
                records = browserViewModel.records.value,
                onHistoryIconClick = { overviewShowsHistory = true },
                onHistoryItemClick = {
                    browserViewModel.loadUrlOrSearch(it.url); showOverview = false
                },
                onHistoryItemLongClick = { _, _ -> },
                addIncognitoTab = {
                    browserViewModel.newTab(BrowserViewModel.DEFAULT_HOME, incognito = true)
                    showOverview = false
                },
                addTab = {
                    browserViewModel.newTab(BrowserViewModel.DEFAULT_HOME)
                    showOverview = false
                },
                closePanel = { showOverview = false },
                onDeleteAction = { browserViewModel.clearHistory() },
                onCloseAllTabs = {
                    browserViewModel.albums.value.toList()
                        .forEach { browserViewModel.closeTab(it) }
                    showOverview = false
                },
                launchNewBrowserAction = {},
            )
        }
    }

    if (isFullscreen) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                    .clickable { isFullscreen = false },
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                androidx.compose.material.Icon(
                    imageVector = Icons.Outlined.FullscreenExit,
                    contentDescription = "Exit fullscreen",
                    tint = MaterialTheme.colors.background,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }

    if (showMenu) {
        Dialog(
            onDismissRequest = { showMenu = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showMenu = false }, scrollable = false) {
                // hasVideo gates the AudioOnly row (Android computes it in
                // WebContentPostProcessor); check the DOM when the menu opens.
                var pageHasVideo by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    engine?.evaluateJavascript(
                        "(document.querySelector('video')!=null).toString()"
                    ) { pageHasVideo = it?.contains("true") == true }
                }
                MenuDialogContent(
                    url = browserViewModel.currentUrl.value,
                    isAudioOnly = helper?.isAudioOnlyOn == true,
                    hasVideo = pageHasVideo,
                    itemClicked = { menuActionHandler.handle(it) },
                    itemLongClicked = { menuActionHandler.handleLongClick(it) },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
    if (showBookmarks) {
        Dialog(
            onDismissRequest = { showBookmarks = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showBookmarks = false }, scrollable = false) {
                BookmarksDialogContent(
                    gotoUrlAction = {
                        browserViewModel.loadUrlOrSearch(it); showBookmarks = false
                    },
                    bookmarkIconClickAction = { title, url, isForeground ->
                        browserViewModel.newTab(url, activate = isForeground, title = title)
                        if (isForeground) showBookmarks = false
                    },
                    splitScreenAction = { url ->
                        browserViewModel.toggleSplitScreen(url); showBookmarks = false
                    },
                    closeAction = { showBookmarks = false },
                )
            }
        }
    }
    if (showFontDialog) {
        Dialog(
            onDismissRequest = { showFontDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = {
                showFontDialog = false
                helper?.updateCssStyle()
            }) {
                if (fontDialogForReader) {
                    ReaderFontDialogContent(
                        onFontCustomizeClick = { helper?.updateCssStyle() },
                        onDismiss = {
                            showFontDialog = false
                            helper?.updateCssStyle()
                        },
                    )
                } else {
                    FontDialogContent(
                        onFontTypeChanged = { helper?.updateCssStyle() },
                        onDismiss = {
                            showFontDialog = false
                            helper?.updateCssStyle()
                        },
                    )
                }
            }
        }
    }
    if (showFastToggle) {
        val dismissFastToggle: () -> Unit = {
            showFastToggle = false
            // Adblock/JS/cookie/incognito toggles take effect on live tabs.
            browserViewModel.reapplyWebConfig()
        }
        Dialog(
            onDismissRequest = dismissFastToggle,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = dismissFastToggle) {
                FastToggleDialogContent(
                    onOpenWhitelist = { type -> showFastToggle = false; showWhitelist = type },
                    onDismiss = {
                        showFastToggle = false
                        // Adblock/JS/cookie/incognito toggles take effect on live tabs.
                        browserViewModel.reapplyWebConfig()
                    },
                )
            }
        }
    }
    if (showSiteSettings) {
        val dismissSiteSettings: () -> Unit = {
            showSiteSettings = false
            // Per-site JS/adblock/UA overrides apply to future loads.
            browserViewModel.reapplyWebConfig()
        }
        Dialog(onDismissRequest = dismissSiteSettings) {
            DialogFrame(onDismiss = dismissSiteSettings) {
                SiteSettingsDialogContent(
                    url = browserViewModel.currentUrl.value,
                    onDismiss = {
                        showSiteSettings = false
                        // Per-site JS/adblock/UA overrides apply to future loads.
                        browserViewModel.reapplyWebConfig()
                    },
                )
            }
        }
    }
    if (showTouchAreaDialog) {
        val dismissTouchArea: () -> Unit = {
            showTouchAreaDialog = false
            touchPagingEnabled = config.touch.enableTouchTurn
        }
        Dialog(
            onDismissRequest = dismissTouchArea,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = dismissTouchArea) {
                TouchAreaDialogContent(
                    onConfigActionsClick = {
                        dismissTouchArea()
                        settingsInitialRoute = info.plateaukao.einkbro.activity.SettingRoute.Gesture
                        showSettings = true
                    },
                    onDismiss = {
                        showTouchAreaDialog = false
                        touchPagingEnabled = config.touch.enableTouchTurn
                    },
                )
            }
        }
    }
    if (showSettings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            SettingsScreen(
                initialRoute = settingsInitialRoute,
                onClose = { showSettings = false },
                onOpenUserScripts = { showSettings = false; showUserScripts = true },
                onOpenGptActions = { showSettings = false; showGptActions = true },
                onOpenGptQueries = { showSettings = false; showGptQueries = true },
                onOpenToolbarConfig = { showSettings = false; showToolbarConfig = true },
                onOpenStatusbarConfig = { showSettings = false; showStatusbarConfig = true },
                onOpenMenuItemHide = { showSettings = false; showMenuItemHide = true },
                onOpenAdBlockSettings = { showSettings = false; showAdBlockSettings = true },
                onOpenWhitelist = { type -> showSettings = false; showWhitelist = type },
            )
        }
    }

    if (showGptActions) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.GptActionsScreen(onClose = { showGptActions = false })
        }
    }

    if (showGptQueries) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.GptQueryListScreen(onClose = { showGptQueries = false })
        }
    }

    if (showStatusbarConfig) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.StatusbarConfigScreen(
                onClose = { showStatusbarConfig = false },
            )
        }
    }

    if (showAdBlockSettings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.AdBlockSettingScreen(
                onClose = { showAdBlockSettings = false },
            )
        }
    }

    showWhitelist?.let { type ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.DataListScreen(
                type = type,
                onClose = { showWhitelist = null },
            )
        }
    }

    // Long-press-a-link context menu.
    browserViewModel.contextMenuLink.value?.let { link ->
        // Dismissing also drops the native word selection the long-press made,
        // so its menu doesn't resurface underneath.
        val dismiss = {
            browserViewModel.contextMenuLink.value = null
            browserViewModel.clearSelection()
        }
        // JS reports the long-press point in viewport CSS px relative to the web
        // pane (1 CSS px = 1 dp, as the selection menu above assumes); shift it by
        // the pane's window origin to get the window pixels the frame places at.
        val anchor = with(LocalDensity.current) {
            Point(
                (link.x.dp.toPx() + webPaneOffset.x).roundToInt(),
                (link.y.dp.toPx() + webPaneOffset.y).roundToInt(),
            )
        }
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PointAnchoredDialogFrame(point = anchor, onDismiss = dismiss) {
                ContextMenuDialogContent(
                    url = link.url,
                    shouldShowAdBlock = false,
                    itemClicked = { handleContextMenuItem(it, link.url) },
                    itemLongClicked = { handleContextMenuLongClick(it, link.url) },
                    onDismiss = dismiss,
                )
            }
        }
    }

    if (showHighlights) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            HighlightsScreen(onClose = { showHighlights = false })
        }
    }

    if (showSavedPages) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            SavedPagesScreen(
                onClose = { showSavedPages = false },
                onOpenPage = { savedPage ->
                    showSavedPages = false
                    browserViewModel.openSavedPage(savedPage.filePath, savedPage.title)
                },
            )
        }
    }

    if (showTtsDialog) {
        Dialog(
            onDismissRequest = { showTtsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showTtsDialog = false }) {
                TtsSettingDialogContent(
                    ttsViewModel = ttsViewModel,
                    readCurrentArticleAction = {
                        helper?.let { current ->
                            scope.launch {
                                val text = current.getRawTextWithCaption()
                                if (text.isNotBlank()) {
                                    ttsViewModel.readArticle(
                                        text, browserViewModel.currentTitle.value
                                    )
                                }
                            }
                        }
                    },
                    onDismiss = { showTtsDialog = false },
                )
            }
        }
    }

    if (showTranslateDialog) {
        Dialog(
            onDismissRequest = { showTranslateDialog = false },
            // Full-width window so the result card sizes itself: near the screen
            // edge on phones, fixed 600dp on tablets (see TranslateResponse).
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DialogFrame(onDismiss = { showTranslateDialog = false }) {
                TranslateDialogContent(
                    translationViewModel = translationViewModel,
                    isWholePageMode = translateDialogWholePage,
                    closeAction = { showTranslateDialog = false },
                )
            }
        }
    }

    if (showTranslationConfig) {
        Dialog(
            onDismissRequest = { showTranslationConfig = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showTranslationConfig = false }) {
                TranslationConfigDialogContent(
                    url = browserViewModel.currentUrl.value,
                    translateDirectly = true,
                    onToggledAction = { shouldTranslate ->
                        if (shouldTranslate) {
                            translateWithMode(
                                config.getTranslationMode(browserViewModel.currentUrl.value)
                            )
                        } else {
                            engine?.reload()
                        }
                    },
                    onDismiss = { showTranslationConfig = false },
                )
            }
        }
    }

    if (showBoldnessDialog) {
        Dialog(
            onDismissRequest = { showBoldnessDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showBoldnessDialog = false }) {
                FontBoldnessContent(
                    fontBoldness = config.display.fontBoldness,
                    onFontBoldnessChanged = {
                        config.display.fontBoldness = it
                        helper?.updateCssStyle()
                    },
                )
            }
        }
    }

    if (showReaderSettings) {
        Dialog(
            onDismissRequest = { showReaderSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showReaderSettings = false }) {
                ReaderSettingsDialogContent(
                    onSettingChanged = { helper?.updateReaderSettingsStyle() },
                    onKeepExtraContentChanged = { /* applies on next reader-mode entry */ },
                    onFontConfigClick = {
                        fontDialogForReader = true
                        showFontDialog = true
                    },
                    onDismiss = { showReaderSettings = false },
                )
            }
        }
    }

    if (showToolbarConfig) {
        val dismissToolbarConfig: () -> Unit = {
            showToolbarConfig = false
            // toolbarActions isn't observable state; poke the toolbar to re-read it.
            toolbarRefreshTick += 1
        }
        // Full-screen arrangement screen (Android's ToolbarConfigActivity):
        // available-actions grid on top, live reorderable toolbar preview below.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.ToolbarConfigScreen(onClose = dismissToolbarConfig)
        }
    }

    if (showPageAiActions) {
        Dialog(
            onDismissRequest = { showPageAiActions = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { showPageAiActions = false }, scrollable = false) {
                PageAiActionDialogContent(
                    actions = config.ai.gptActionList
                        .filter { it.scope == GptActionScope.WholePage },
                    onActionClicked = { gptAction ->
                        showPageAiActions = false
                        // Android runPageAiAction: the action's display decides
                        // popup vs chat tab (NewTab/SplitScreen both map to a
                        // native chat tab here — split-pane chat is deferred).
                        if (gptAction.display != info.plateaukao.einkbro.preference.GptActionDisplay.Popup) {
                            handleBrowserAction(
                                BrowserAction.ChatWithWeb(runWithAction = gptAction)
                            )
                        } else helper?.let { current ->
                            scope.launch {
                                val text = current.getRawTextWithCaption()
                                if (text.isNotBlank()) {
                                    translationViewModel.url = browserViewModel.currentUrl.value
                                    translationViewModel.pageTitle =
                                        browserViewModel.currentTitle.value
                                    translationViewModel.updateInputMessage(text)
                                    translationViewModel.setupGptAction(gptAction)
                                    translateDialogWholePage = true
                                    showTranslateDialog = true
                                }
                            }
                        }
                    },
                    onChatWithWebClicked = {
                        showPageAiActions = false
                        handleBrowserAction(BrowserAction.ChatWithWeb(useSplitScreen = false))
                    },
                    onChatWithWebLongClicked = {
                        showPageAiActions = false
                        handleBrowserAction(BrowserAction.ChatWithWeb(useSplitScreen = true))
                    },
                    onTaskRunnerClicked = {
                        showPageAiActions = false
                        showTaskMenu = true
                    },
                    onSettingsClicked = {
                        showPageAiActions = false
                        showGptActions = true
                    },
                    onDismiss = { showPageAiActions = false },
                )
            }
        }
    }

    languageConfigApi?.let { api ->
        Dialog(onDismissRequest = { languageConfigApi = null }) {
            Surface(color = MaterialTheme.colors.background) {
                LanguageSettingDialogContent(
                    translateApi = api,
                    translationViewModel = translationViewModel,
                    translate = {
                        languageConfigApi = null
                        translateByParagraphWith(api)
                    },
                    onDismiss = { languageConfigApi = null },
                )
            }
        }
    }

    tocItems?.let { chapters ->
        Dialog(onDismissRequest = { tocItems = null }) {
            Surface(color = MaterialTheme.colors.background) {
                TocDialogContent(
                    chapters = chapters,
                    isEditable = false,
                    onNavigate = { index ->
                        browserViewModel.currentEngine?.evaluateJavascript(
                            Assets.get("goto_toc.js").replace("__INDEX__", index.toString())
                        )
                        tocItems = null
                    },
                    onDismiss = { tocItems = null },
                )
            }
        }
    }

    if (showEpubDialog) {
        val epubProgress = browserViewModel.epubProgress.value
        val doneTick = browserViewModel.epubDoneTick.value
        var savedEpubs by remember { mutableStateOf(config.savedEpubFileInfos) }
        // Dismiss when the export finishes: a tick change is always delivered,
        // unlike the fast progress transitions (which Compose can coalesce).
        val openedAtTick = remember { doneTick }
        LaunchedEffect(doneTick) { if (doneTick != openedAtTick) showEpubDialog = false }
        EpubDialog(
            defaultTitle = browserViewModel.currentTitle.value.ifBlank { "page" },
            savedEpubs = savedEpubs,
            progress = epubProgress,
            onSaveNew = { book, chap -> browserViewModel.exportEpub(book, chap, null) },
            onAppend = { chap, path -> browserViewModel.exportEpub("", chap, path) },
            onRemove = { info -> browserViewModel.removeSavedEpub(info); savedEpubs = config.savedEpubFileInfos },
            onDismiss = { showEpubDialog = false },
        )
    }

    if (showInstapaperConfig) {
        InstapaperDialog(
            initialUsername = config.instapaperUsername,
            initialPassword = config.instapaperPassword,
            onSave = { user, pass ->
                browserViewModel.saveInstapaperCredentials(user, pass)
                showInstapaperConfig = false
                // If saved from the "Add" flow, add the page right away.
                if (browserViewModel.hasInstapaperCredentials()) browserViewModel.addToInstapaper()
            },
            onDismiss = { showInstapaperConfig = false },
        )
    }

    if (showUserScripts) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            UserScriptListScreen(onClose = { showUserScripts = false })
        }
    }

    if (showMenuItemHide) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            info.plateaukao.einkbro.activity.MenuItemHideScreen(
                onClose = { showMenuItemHide = false },
            )
        }
    }

    if (showTaskMenu) {
        Dialog(onDismissRequest = { showTaskMenu = false }) {
            DialogFrame(onDismiss = { showTaskMenu = false }) {
                info.plateaukao.einkbro.view.dialog.compose.TaskMenuDialogContent(
                    onTemplateClicked = { descriptor ->
                        showTaskMenu = false
                        handleBrowserAction(BrowserAction.RunTask(descriptor.id))
                    },
                    onCustomClicked = {
                        showTaskMenu = false
                        scope.launch {
                            val prompt = AppServices.dialogManager.getTextInput(
                                Res.string.task_custom_title,
                                Res.string.task_custom_hint,
                                "",
                            ) ?: return@launch
                            handleBrowserAction(BrowserAction.RunCustomTask(prompt))
                        }
                    },
                    onDismiss = { showTaskMenu = false },
                )
            }
        }
    }

    // A *.user.js navigation was intercepted: open the manager in install mode
    // (Android launches UserScriptListActivity with the script URL).
    browserViewModel.pendingUserScriptInstall.value?.let { installUrl ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            UserScriptListScreen(
                installUrl = installUrl,
                onClose = { browserViewModel.pendingUserScriptInstall.value = null },
            )
        }
    }

    // Userscript menu-command picker (GM_registerMenuCommand, parity Phase H).
    if (userScriptCommands.isNotEmpty()) {
        val commands = userScriptCommands
        Dialog(
            onDismissRequest = { userScriptCommands = emptyList() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnchoredDialogFrame(onDismiss = { userScriptCommands = emptyList() }) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    commands.forEach { (caption, fnId) ->
                        androidx.compose.material.Text(
                            caption,
                            color = MaterialTheme.colors.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    browserViewModel.invokeUserScriptMenuCommand(fnId)
                                    userScriptCommands = emptyList()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    // Confirm-tab-close (parity Phase C, confirmTabClose pref).
    browserViewModel.pendingTabClose.value?.let { album ->
        val cancel = { browserViewModel.pendingTabClose.value = null }
        Dialog(onDismissRequest = cancel) {
            Surface(color = MaterialTheme.colors.background) {
                Column(Modifier.padding(16.dp)) {
                    androidx.compose.material.Text(
                        "Close this tab?",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onBackground,
                    )
                    androidx.compose.material.Text(
                        album.albumTitle.ifBlank { "Current tab" },
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colors.onBackground,
                    )
                    androidx.compose.foundation.layout.Row(Modifier.align(Alignment.End)) {
                        androidx.compose.material.TextButton(onClick = cancel) {
                            androidx.compose.material.Text(
                                "Cancel", color = MaterialTheme.colors.onBackground,
                            )
                        }
                        androidx.compose.material.TextButton(
                            onClick = { browserViewModel.confirmPendingTabClose() }
                        ) {
                            androidx.compose.material.Text(
                                "Close", color = MaterialTheme.colors.onBackground,
                            )
                        }
                    }
                }
            }
        }
    }

    // Engine-delegate requests (parity Phase B): HTTP auth, TLS trust, JS panels.
    browserViewModel.pendingAuthRequest.value?.let { request ->
        val finish: (Pair<String, String>?) -> Unit = {
            request.respond(it)
            browserViewModel.pendingAuthRequest.value = null
        }
        Dialog(onDismissRequest = { finish(null) }) {
            Surface(color = MaterialTheme.colors.background) {
                AuthenticationDialogContent(
                    okAction = { username, password -> finish(username to password) },
                    onDismiss = { finish(null) },
                )
            }
        }
    }

    browserViewModel.pendingSslError.value?.let { request ->
        val finish: (Boolean) -> Unit = {
            request.respond(it)
            browserViewModel.pendingSslError.value = null
        }
        Dialog(onDismissRequest = { finish(false) }) {
            Surface(color = MaterialTheme.colors.background) {
                SslErrorDialogContent(host = request.host, onResult = finish)
            }
        }
    }

    browserViewModel.pendingJsDialog.value?.let { request ->
        val finish: (Boolean, String?) -> Unit = { confirmed, text ->
            request.respond(confirmed, text)
            browserViewModel.pendingJsDialog.value = null
        }
        Dialog(onDismissRequest = { finish(false, null) }) {
            Surface(color = MaterialTheme.colors.background) {
                JsPanelDialogContent(request = request, onResult = finish)
            }
        }
    }
}

/** Certificate-error dialog (Android's SSL warning equivalent). */
@Composable
private fun SslErrorDialogContent(host: String, onResult: (Boolean) -> Unit) {
    Column(Modifier.padding(16.dp)) {
        androidx.compose.material.Text(
            "Untrusted certificate",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground,
        )
        androidx.compose.material.Text(
            "The identity of $host can't be verified. Load the page anyway?",
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colors.onBackground,
        )
        androidx.compose.foundation.layout.Row(Modifier.align(Alignment.End)) {
            androidx.compose.material.TextButton(onClick = { onResult(false) }) {
                androidx.compose.material.Text(
                    "Cancel", color = MaterialTheme.colors.onBackground,
                )
            }
            androidx.compose.material.TextButton(onClick = { onResult(true) }) {
                androidx.compose.material.Text(
                    "Proceed", color = MaterialTheme.colors.onBackground,
                )
            }
        }
    }
}

/** JS alert/confirm/prompt panel — one dialog for all three panel types. */
@Composable
private fun JsPanelDialogContent(
    request: info.plateaukao.einkbro.browser.JsDialogRequest,
    onResult: (Boolean, String?) -> Unit,
) {
    var promptText by remember { mutableStateOf(request.defaultText.orEmpty()) }
    Column(Modifier.padding(16.dp)) {
        androidx.compose.material.Text(
            request.message,
            color = MaterialTheme.colors.onBackground,
        )
        if (request.type == info.plateaukao.einkbro.browser.JsDialogType.PROMPT) {
            androidx.compose.material.OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.End).padding(top = 12.dp)
        ) {
            if (request.type != info.plateaukao.einkbro.browser.JsDialogType.ALERT) {
                androidx.compose.material.TextButton(onClick = { onResult(false, null) }) {
                    androidx.compose.material.Text(
                        "Cancel", color = MaterialTheme.colors.onBackground,
                    )
                }
            }
            androidx.compose.material.TextButton(onClick = { onResult(true, promptText) }) {
                androidx.compose.material.Text(
                    "OK", color = MaterialTheme.colors.onBackground,
                )
            }
        }
    }
}

/** Drops the query string and fragment (Android BrowserUnit.stripUrlQuery). */
private fun stripUrlQuery(url: String): String =
    url.substringBefore('?').substringBefore('#')

private val tocJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class TocEntry(val level: Int = 1, val text: String = "")

/**
 * Split-screen second pane (parity Phase G): the split engine's web view under a
 * compact control bar — rotate orientation, swap panes, link-here + scroll-sync
 * toggles (bold when on), font +/-, close.
 */
@Composable
private fun SplitPane(
    modifier: Modifier,
    browserViewModel: BrowserViewModel,
    statusBarSuppressed: Boolean,
) {
    val config = AppServices.config
    val splitEngine = browserViewModel.splitEngine
    val splitAlbum = browserViewModel.splitAlbum.value
    var linkHere by remember { mutableStateOf(config.translation.twoPanelLinkHere) }
    var scrollSync by remember { mutableStateOf(config.translation.translationScrollSync) }
    Column(modifier.background(MaterialTheme.colors.background)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth()
                // The main pane's WKWebView avoids the notch itself; this Compose
                // bar must too when the shared inset drops the top (edge-to-edge).
                .then(
                    if (statusBarSuppressed)
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
                .height(38.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SplitBarButton("Rotate") { browserViewModel.toggleSplitOrientation() }
            SplitBarButton("Swap") { browserViewModel.swapSplitPanes() }
            SplitBarButton("Link", active = linkHere) {
                linkHere = !linkHere
                config.translation.twoPanelLinkHere = linkHere
            }
            SplitBarButton("Sync", active = scrollSync) {
                scrollSync = !scrollSync
                config.translation.translationScrollSync = scrollSync
            }
            SplitBarButton("A-") { browserViewModel.adjustSplitFont(-20) }
            SplitBarButton("A+") { browserViewModel.adjustSplitFont(20) }
            SplitBarButton("Close") { browserViewModel.closeSplitScreen() }
        }
        androidx.compose.material.Divider(
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f),
        )
        if (splitEngine != null && splitAlbum != null) {
            key(splitAlbum.id) {
                WebViewHost(splitEngine, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SplitBarButton(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material.Text(
        label,
        color = MaterialTheme.colors.onBackground,
        fontSize = 13.sp,
        fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.Bold
        else androidx.compose.ui.text.font.FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Port of Android BookmarkRenderer.getRecentBookmarksContent. */
private fun recentBookmarksHtml(
    config: info.plateaukao.einkbro.preference.ConfigManager,
): String {
    val stored = config.recentBookmarks
    if (stored.isEmpty()) return ""
    val alignBottom = !config.ui.isToolbarOnTop
    val bookmarks = if (alignBottom) stored.reversed() else stored
    val content = bookmarks.joinToString(separator = "\n") {
        val initial = it.name.firstOrNull()?.uppercase() ?: "#"
        val host = info.plateaukao.einkbro.util.Uri.parse(it.url).host.orEmpty()
        val domain = host.removePrefix("www.")
        val scheme = it.url.substringBefore("://", "https")
        val faviconUrl = if (host.isNotEmpty()) "$scheme://$host/favicon.ico" else ""
        """
        <a href="${it.url}" class="card">
            <div class="icon">
                <img src="$faviconUrl" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" />
                <span class="fallback">$initial</span>
            </div>
            <div class="info">
                <div class="name">${it.name}</div>
                <div class="domain">$domain</div>
            </div>
        </a>
        """
    }
    val bodyClass = if (alignBottom) "align-bottom" else ""
    return info.plateaukao.einkbro.browser.Assets.get("recent_bookmarks.html")
        .replace("{{BODY_CLASS}}", bodyClass)
        .replace("{{CONTENT}}", content)
}
