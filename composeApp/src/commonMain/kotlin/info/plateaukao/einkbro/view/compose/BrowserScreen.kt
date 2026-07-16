package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.activity.HighlightsScreen
import info.plateaukao.einkbro.activity.SavedPagesScreen
import info.plateaukao.einkbro.activity.SettingsScreen
import info.plateaukao.einkbro.activity.UserScriptListScreen
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.browser.WebViewHost
import info.plateaukao.einkbro.catalog.DialogFrame
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.preference.ShareLongPressAction
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.resources.Res
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
import info.plateaukao.einkbro.view.dialog.compose.LanguageSettingDialogContent
import info.plateaukao.einkbro.view.dialog.compose.MenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.PageAiActionDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ReaderSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TocDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TocItem
import info.plateaukao.einkbro.view.dialog.compose.ToolbarConfigDialogContent
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
    onOpenCatalog: () -> Unit = {},
) {
    val config = AppServices.config
    var showTabStrip by remember { mutableStateOf(config.tab.shouldShowTabBar) }
    var showOverview by remember { mutableStateOf(false) }
    var overviewShowsHistory by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
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
    var showPageAiActions by remember { mutableStateOf(false) }
    var showUserScripts by remember { mutableStateOf(false) }
    var languageConfigApi by remember { mutableStateOf<TRANSLATE_API?>(null) }
    var tocItems by remember { mutableStateOf<List<TocItem>?>(null) }
    var toolbarRefreshTick by remember { mutableStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var touchPagingEnabled by remember { mutableStateOf(config.touch.enableTouchTurn) }
    val scope = rememberCoroutineScope()

    // Host pref (parity Phase D): keep-awake applies at startup and on change.
    LaunchedEffect(config.ui.keepAwake) {
        info.plateaukao.einkbro.util.HostBridge.setKeepAwake(config.ui.keepAwake)
    }
    // Status-bar hide / fullscreen: the Compose root goes edge-to-edge under
    // the status bar (pixel-true hiding needs a Swift VC override — deferred).
    val statusBarSuppressed = config.ui.hideStatusbar || isFullscreen

    // Session-scoped services (Phase 6): reading continues after the TTS
    // dialog closes, and translate results survive reopening the popup.
    val ttsViewModel = remember { TtsViewModel() }
    val translationViewModel = remember { TranslationViewModel() }

    LaunchedEffect(Unit) {
        info.plateaukao.einkbro.browser.Assets.preload()
        // Compile the adblock rule list once; re-apply to tabs when it's ready.
        info.plateaukao.einkbro.browser.ContentBlocker.preload(
            info.plateaukao.einkbro.browser.Assets.get("adblock_rules.json")
        ) { browserViewModel.reapplyWebConfig() }
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
    fun translateWithMode(mode: TranslationMode) {
        val currentHelper = helper ?: return
        val bridge = browserViewModel.translationBridge
        when (mode) {
            TranslationMode.TRANSLATE_BY_PARAGRAPH -> {
                bridge.translateApi = TRANSLATE_API.GOOGLE
                currentHelper.translateByParagraph()
            }

            TranslationMode.DEEPL_BY_PARAGRAPH -> {
                bridge.translateApi = TRANSLATE_API.DEEPL
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

            // Google widget injection (Phase M) and Papago screen OCR (Phase M).
            TranslationMode.GOOGLE_IN_PLACE ->
                EBToast.show(AppServices.context, "Google in-place translate: coming in Phase M")
            TranslationMode.PAPAGO_TRANSLATE_BY_SCREEN ->
                EBToast.show(AppServices.context, "Papago screen translate: coming in Phase M")
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
                    browserViewModel.newTab("", title = "New tab")
                    showBookmarks = true
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
                config.display.fontSize = (config.display.fontSize + 20).coerceAtMost(300)
                currentHelper?.updateCssStyle()
            }
            BrowserAction.DecreaseFontSize -> {
                config.display.fontSize = (config.display.fontSize - 20).coerceAtLeast(50)
                currentHelper?.updateCssStyle()
            }
            BrowserAction.ShowFontSizeChangeDialog -> showFontDialog = true
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
                EBToast.show(
                    AppServices.context,
                    if (isFullscreen) "Fullscreen on — tap ⤢ or long-press refresh to exit"
                    else "Fullscreen off"
                )
            }
            is BrowserAction.ToggleSplitScreen -> comingSoon("Split screen", 'G')

            // Translation
            BrowserAction.ShowTranslation -> {
                if (currentHelper?.isTranslateByParagraph == true) {
                    currentHelper.clearTranslationElements()
                    EBToast.show(AppServices.context, "Translation cleared")
                } else {
                    showTranslationConfig = true
                }
            }
            is BrowserAction.ShowTranslationConfigDialog -> showTranslationConfig = true
            is BrowserAction.Translate -> translateWithMode(action.mode)
            is BrowserAction.ConfigureTranslationLanguage -> languageConfigApi = action.api

            // TTS
            BrowserAction.HandleTtsButton -> {
                if (!ttsViewModel.isReading()) {
                    currentHelper?.getRawText { text ->
                        if (text.isNotBlank()) {
                            ttsViewModel.readArticle(text, browserViewModel.currentTitle.value)
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
            BrowserAction.ShowSearchPanel -> comingSoon("Find on page", 'E')
            BrowserAction.ToggleTextSearch -> comingSoon("Remote text search", 'J')
            BrowserAction.ToggleReceiveTextSearch -> comingSoon("Remote text search", 'J')

            // Share
            BrowserAction.CreateShortcut ->
                EBToast.show(AppServices.context, "iOS apps can't add home-screen shortcuts")
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
            is BrowserAction.SendToRemote -> comingSoon("LAN link sharing", 'J')
            BrowserAction.AddToInstapaper -> comingSoon("Instapaper", 'J')
            BrowserAction.ConfigureInstapaper -> comingSoon("Instapaper", 'J')
            BrowserAction.ToggleReceiveLink -> comingSoon("LAN link sharing", 'J')

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
                    currentHelper?.getRawText { text ->
                        if (text.isNotBlank()) {
                            translationViewModel.url = browserViewModel.currentUrl.value
                            translationViewModel.pageTitle = browserViewModel.currentTitle.value
                            translationViewModel.setupTextSummary(text)
                            translateDialogWholePage = true
                            showTranslateDialog = true
                        }
                    } ?: Unit
                }
            }
            is BrowserAction.ChatWithWeb -> comingSoon("Chat with web", 'K')
            BrowserAction.ShowPageAiActionMenu -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    showPageAiActions = true
                }
            }
            BrowserAction.ShowTaskMenu -> comingSoon("Task runner", 'K')
            is BrowserAction.RunTask -> comingSoon("Task runner", 'K')
            is BrowserAction.RunCustomTask -> comingSoon("Task runner", 'K')

            // File
            BrowserAction.ShowEpubDialog -> comingSoon("EPUB export", 'I')
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
            BrowserAction.ShowUserScriptCommands -> showUserScripts = true

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
            BrowserAction.OpenSettings -> showSettings = true
            BrowserAction.ShowHighlights -> showHighlights = true
            BrowserAction.OpenUserScriptManager -> showUserScripts = true
        }
    }

    val toolbarActionHandler = ToolbarActionHandler { handleBrowserAction(it) }
    val menuActionHandler = MenuActionHandler(
        dispatch = { handleBrowserAction(it) },
        currentUrl = { browserViewModel.currentUrl.value },
        quit = onOpenCatalog,
    )

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
            ContextMenuItemType.SplitScreen -> comingSoon("Split screen", 'G')
            ContextMenuItemType.Summarize -> comingSoon("Link summarize", 'K')
            ContextMenuItemType.Tts -> comingSoon("Read link aloud", 'K')
            ContextMenuItemType.SaveAs -> browserViewModel.currentEngine?.startDownload(url)
            ContextMenuItemType.TranslateImage -> comingSoon("Image translation", 'M')
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
            ContextMenuItemType.TranslateImage -> comingSoon("Image translation", 'M')
            else -> Unit
        }
    }

    val rootInsets = if (statusBarSuppressed) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    } else {
        WindowInsets.safeDrawing
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(rootInsets)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (engine != null) {
                // Key by tab id so UIKitView re-embeds the current tab's
                // WKWebView when the active tab changes (its factory runs once).
                key(browserViewModel.currentAlbum?.id) {
                    WebViewHost(engine, Modifier.fillMaxSize())
                }
            }

            if (touchPagingEnabled) {
                // Vertical-rl reading advances leftward, so the zones flip in
                // vertical mode (same as Android's dispatchTouchEvent handling).
                Box(
                    Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight(0.6f)
                        .clickable {
                            if (helper?.isVerticalRead == true) helper.pageDown()
                            else helper?.pageUp() ?: engine?.pageUp()
                        }
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight(0.6f)
                        .clickable {
                            if (helper?.isVerticalRead == true) helper.pageUp()
                            else helper?.pageDown() ?: engine?.pageDown()
                        }
                )
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
                                drawable = Res.drawable.ic_highlight_color,
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
                        )
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

            if (showOverview) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    HistoryAndTabs(
                        isHistoryOpen = overviewShowsHistory,
                        albumList = browserViewModel.albums,
                        albumFocusIndex = browserViewModel.focusIndex,
                        onTabIconClick = { overviewShowsHistory = false },
                        onTabClick = { browserViewModel.switchTab(it); showOverview = false },
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    AutoCompleteTextField(
                        focusRequester = urlFocusRequester,
                        // Behavior pref: surface bookmarks (with favicons) in the
                        // input bar's suggestion list.
                        bookmarkManager = if (config.browser.showBookmarksInInputBar)
                            AppServices.bookmarkManager else null,
                        text = text,
                        recordList = recordsState,
                        onTextSubmit = {
                            browserViewModel.loadUrlOrSearch(it); showUrlInput = false
                        },
                        onTextChange = {},
                        onPasteClick = {},
                        closeAction = { showUrlInput = false },
                        onRecordClick = {
                            browserViewModel.loadUrlOrSearch(it.url); showUrlInput = false
                        },
                    )
                }
            }
        }

        if (progress < 1f) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colors.onBackground,
            )
        }

        // Fullscreen (parity Phase D) hides the toolbar; a small exit chip
        // brings it back (iOS has no back key to restore it like Android).
        if (!isFullscreen) {
            ComposedToolbar(
                showTabs = showTabStrip,
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
                onAlbumClick = { browserViewModel.switchTab(it) },
                onAlbumLongClick = { browserViewModel.closeTab(it) },
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
        Dialog(onDismissRequest = { showMenu = false }) {
            Surface(color = MaterialTheme.colors.background) {
                MenuDialogContent(
                    url = browserViewModel.currentUrl.value,
                    itemClicked = { menuActionHandler.handle(it) },
                    itemLongClicked = { menuActionHandler.handleLongClick(it) },
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
    if (showBookmarks) {
        Dialog(onDismissRequest = { showBookmarks = false }) {
            Surface(color = MaterialTheme.colors.background) {
                BookmarksDialogContent(
                    gotoUrlAction = {
                        browserViewModel.loadUrlOrSearch(it); showBookmarks = false
                    },
                    bookmarkIconClickAction = { title, url, isForeground ->
                        browserViewModel.newTab(url, activate = isForeground, title = title)
                        if (isForeground) showBookmarks = false
                    },
                    splitScreenAction = { comingSoon("Split screen", 'G') },
                    closeAction = { showBookmarks = false },
                )
            }
        }
    }
    if (showFontDialog) {
        Dialog(onDismissRequest = { showFontDialog = false }) {
            DialogFrame(onDismiss = {
                showFontDialog = false
                helper?.updateCssStyle()
            }) {
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
    if (showFastToggle) {
        val dismissFastToggle: () -> Unit = {
            showFastToggle = false
            // Adblock/JS/cookie/incognito toggles take effect on live tabs.
            browserViewModel.reapplyWebConfig()
        }
        Dialog(onDismissRequest = dismissFastToggle) {
            DialogFrame(onDismiss = dismissFastToggle) {
                FastToggleDialogContent(onDismiss = {
                    showFastToggle = false
                    // Adblock/JS/cookie/incognito toggles take effect on live tabs.
                    browserViewModel.reapplyWebConfig()
                })
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
        Dialog(onDismissRequest = dismissTouchArea) {
            DialogFrame(onDismiss = dismissTouchArea) {
                TouchAreaDialogContent(onDismiss = {
                    showTouchAreaDialog = false
                    touchPagingEnabled = config.touch.enableTouchTurn
                })
            }
        }
    }
    if (showSettings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            SettingsScreen(onClose = { showSettings = false })
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
        Dialog(onDismissRequest = dismiss) {
            Surface(color = MaterialTheme.colors.background) {
                ContextMenuDialogContent(
                    url = link,
                    shouldShowAdBlock = false,
                    shouldShowTranslateImage = false,
                    itemClicked = { handleContextMenuItem(it, link) },
                    itemLongClicked = { handleContextMenuLongClick(it, link) },
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
        Dialog(onDismissRequest = { showTtsDialog = false }) {
            DialogFrame(onDismiss = { showTtsDialog = false }) {
                TtsSettingDialogContent(
                    ttsViewModel = ttsViewModel,
                    onDismiss = { showTtsDialog = false },
                )
            }
        }
    }

    if (showTranslateDialog) {
        Dialog(onDismissRequest = { showTranslateDialog = false }) {
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
        Dialog(onDismissRequest = { showTranslationConfig = false }) {
            DialogFrame(onDismiss = { showTranslationConfig = false }) {
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
        Dialog(onDismissRequest = { showBoldnessDialog = false }) {
            Surface(color = MaterialTheme.colors.background) {
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
        Dialog(onDismissRequest = { showReaderSettings = false }) {
            Surface(color = MaterialTheme.colors.background) {
                ReaderSettingsDialogContent(
                    onSettingChanged = { helper?.updateReaderSettingsStyle() },
                    onKeepExtraContentChanged = { /* applies on next reader-mode entry */ },
                    onFontConfigClick = { showFontDialog = true },
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
        Dialog(onDismissRequest = dismissToolbarConfig) {
            Surface(color = MaterialTheme.colors.background) {
                ToolbarConfigDialogContent(onDismiss = dismissToolbarConfig)
            }
        }
    }

    if (showPageAiActions) {
        Dialog(onDismissRequest = { showPageAiActions = false }) {
            Surface(color = MaterialTheme.colors.background) {
                PageAiActionDialogContent(
                    actions = config.ai.gptActionList,
                    onActionClicked = { gptAction ->
                        showPageAiActions = false
                        helper?.getRawText { text ->
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
                    },
                    onChatWithWebClicked = { comingSoon("Chat with web", 'K') },
                    onChatWithWebLongClicked = { comingSoon("Chat with web", 'K') },
                    onTaskRunnerClicked = { comingSoon("Task runner", 'K') },
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

    if (showUserScripts) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            UserScriptListScreen(onClose = { showUserScripts = false })
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
