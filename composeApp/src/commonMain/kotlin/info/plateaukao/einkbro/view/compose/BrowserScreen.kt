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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
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
import info.plateaukao.einkbro.activity.SettingsScreen
import info.plateaukao.einkbro.browser.WebViewHost
import info.plateaukao.einkbro.catalog.DialogFrame
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.preference.toggle
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.ic_highlight_color
import info.plateaukao.einkbro.util.PlatformActions
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.data.MenuInfo
import info.plateaukao.einkbro.view.dialog.compose.ActionModeMenu
import info.plateaukao.einkbro.view.dialog.compose.BookmarksDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ContextMenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ContextMenuItemType
import info.plateaukao.einkbro.view.dialog.compose.FastToggleDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.MenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.MenuItemType
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TouchAreaDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslateDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslationConfigDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TtsSettingDialogContent
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo
import info.plateaukao.einkbro.viewmodel.BrowserViewModel
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API
import info.plateaukao.einkbro.viewmodel.TranslationViewModel
import info.plateaukao.einkbro.viewmodel.TtsViewModel
import kotlinx.coroutines.launch

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
    var showTabStrip by remember { mutableStateOf(false) }
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
    var showTtsDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var translateDialogWholePage by remember { mutableStateOf(false) }
    var showTranslationConfig by remember { mutableStateOf(false) }
    var touchPagingEnabled by remember { mutableStateOf(config.touch.enableTouchTurn) }
    val scope = rememberCoroutineScope()

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

    val engine = browserViewModel.currentEngine
    val helper = browserViewModel.currentHelper
    val progress by browserViewModel.progress

    fun handleToolbarAction(action: ToolbarAction) {
        when (action) {
            ToolbarAction.Back -> if (engine?.canGoBack() == true) engine.goBack()
            else browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) }

            ToolbarAction.Forward -> engine?.goForward()
            ToolbarAction.Refresh -> engine?.reload()
            ToolbarAction.PageUp -> helper?.pageUp() ?: engine?.pageUp()
            ToolbarAction.PageDown -> helper?.pageDown() ?: engine?.pageDown()
            ToolbarAction.ReaderMode -> helper?.toggleReaderMode()
            ToolbarAction.VerticalLayout -> helper?.toggleVerticalRead()
            ToolbarAction.InvertColor -> helper?.toggleInvertColor()
            ToolbarAction.BoldFont -> {
                config.display.boldFontStyle = !config.display.boldFontStyle
                helper?.updateCssStyle()
            }
            ToolbarAction.IncreaseFont -> {
                config.display.fontSize = (config.display.fontSize + 20).coerceAtMost(300)
                helper?.updateCssStyle()
            }
            ToolbarAction.DecreaseFont -> {
                config.display.fontSize = (config.display.fontSize - 20).coerceAtLeast(50)
                helper?.updateCssStyle()
            }
            ToolbarAction.Title, ToolbarAction.InputUrl -> showUrlInput = true
            ToolbarAction.TabCount -> showOverview = !showOverview
            ToolbarAction.NewTab -> browserViewModel.newTab(BrowserViewModel.DEFAULT_HOME)
            ToolbarAction.CloseTab ->
                browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) }

            ToolbarAction.Bookmark -> showBookmarks = true
            ToolbarAction.Settings -> showMenu = true
            ToolbarAction.Font -> showFontDialog = true
            ToolbarAction.Touch -> {
                config.touch.enableTouchTurn = !touchPagingEnabled
                touchPagingEnabled = !touchPagingEnabled
                EBToast.show(
                    AppServices.context,
                    if (touchPagingEnabled) "Touch paging on" else "Touch paging off"
                )
            }

            ToolbarAction.DuplicateTab ->
                browserViewModel.newTab(browserViewModel.currentUrl.value)

            else -> EBToast.show(AppServices.context, "${action.name}: later phase")
        }
    }

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

            // Google widget injection and Papago screen OCR: later phase.
            TranslationMode.GOOGLE_IN_PLACE,
            TranslationMode.PAPAGO_TRANSLATE_BY_SCREEN,
            -> EBToast.show(AppServices.context, "${mode.name}: later phase")
        }
    }

    fun handleMenuItem(item: MenuItemType) {
        showMenu = false
        when (item) {
            MenuItemType.CloseTab ->
                browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) }

            MenuItemType.OpenHome -> engine?.loadUrl(BrowserViewModel.DEFAULT_HOME)
            MenuItemType.Settings -> showSettings = true
            MenuItemType.QuickToggle -> showFastToggle = true
            MenuItemType.SiteSettings -> showSiteSettings = true
            MenuItemType.FontSize -> showFontDialog = true
            MenuItemType.TouchSetting -> showTouchAreaDialog = true
            MenuItemType.Highlights -> showHighlights = true
            MenuItemType.Tts -> {
                if (ttsViewModel.isReading()) {
                    showTtsDialog = true
                } else {
                    helper?.getRawText { text ->
                        if (text.isNotBlank()) {
                            ttsViewModel.readArticle(text, browserViewModel.currentTitle.value)
                        }
                    }
                    showTtsDialog = true
                }
            }

            MenuItemType.Translate -> {
                if (helper?.isTranslateByParagraph == true) {
                    helper.clearTranslationElements()
                    EBToast.show(AppServices.context, "Translation cleared")
                } else {
                    showTranslationConfig = true
                }
            }

            MenuItemType.PageAiActions -> {
                if (!translationViewModel.hasOpenAiApiKey() &&
                    config.ai.geminiApiKey.isBlank() && !config.ai.useCustomGptUrl
                ) {
                    EBToast.show(AppServices.context, "Set an AI API key in Settings first")
                } else {
                    helper?.getRawText { text ->
                        if (text.isNotBlank()) {
                            translationViewModel.url = browserViewModel.currentUrl.value
                            translationViewModel.pageTitle = browserViewModel.currentTitle.value
                            translationViewModel.setupTextSummary(text)
                            translateDialogWholePage = true
                            showTranslateDialog = true
                        }
                    }
                }
            }

            MenuItemType.ReaderMode -> helper?.toggleReaderMode()
            MenuItemType.VerticalRead -> helper?.toggleVerticalRead()
            MenuItemType.InvertColor -> helper?.toggleInvertColor()
            MenuItemType.AudioOnly -> helper?.toggleAudioOnly()
            MenuItemType.BoldFont -> {
                config.display.boldFontStyle = !config.display.boldFontStyle
                helper?.updateCssStyle()
            }
            MenuItemType.BlackFont -> {
                config.display.blackFontStyle = !config.display.blackFontStyle
                helper?.updateCssStyle()
            }
            MenuItemType.WhiteBknd -> {
                config.toggleWhiteBackground(browserViewModel.currentUrl.value)
                helper?.updateCssStyle()
            }
            MenuItemType.SaveBookmark -> {
                val album = browserViewModel.currentAlbum ?: return
                val title = album.albumTitle
                val url = browserViewModel.currentUrl.value
                scope.launch {
                    AppServices.bookmarkManager.insert(
                        info.plateaukao.einkbro.database.Bookmark(title = title, url = url)
                    )
                    EBToast.show(AppServices.context, "Bookmark saved")
                }
            }

            MenuItemType.Quit -> onOpenCatalog()
            else -> EBToast.show(AppServices.context, "${item.name}: later phase")
        }
    }

    fun handleContextMenuItem(item: ContextMenuItemType, url: String) {
        when (item) {
            ContextMenuItemType.NewTabForeground -> browserViewModel.newTab(url)
            ContextMenuItemType.NewTabBackground ->
                browserViewModel.newTab(url, activate = false)

            ContextMenuItemType.ShareLink -> PlatformActions.share(url)
            ContextMenuItemType.OpenWith -> PlatformActions.openUrl(url)
            else -> EBToast.show(AppServices.context, "${item.name}: later phase")
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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

        ComposedToolbar(
            showTabs = showTabStrip,
            toolbarActionInfos = config.ui.toolbarActions.map { ToolbarActionInfo(it, false) },
            title = browserViewModel.currentTitle.value
                .ifBlank { browserViewModel.currentUrl.value },
            tabCount = browserViewModel.albums.value.size.toString(),
            pageInfo = "",
            isIncognito = browserViewModel.currentAlbum?.incognito == true,
            onIconClick = { handleToolbarAction(it) },
            onIconLongClick = {
                when (it) {
                    ToolbarAction.Touch -> showTouchAreaDialog = true
                    ToolbarAction.TabCount -> showTabStrip = !showTabStrip
                    else -> {}
                }
            },
            albumList = browserViewModel.albums,
            albumFocusIndex = browserViewModel.focusIndex,
            onAlbumClick = { browserViewModel.switchTab(it) },
            onAlbumLongClick = { browserViewModel.closeTab(it) },
        )
    }

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Surface(color = MaterialTheme.colors.background) {
                MenuDialogContent(
                    url = browserViewModel.currentUrl.value,
                    itemClicked = { handleMenuItem(it) },
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
                    closeAction = { showBookmarks = false },
                )
            }
        }
    }
    if (showFontDialog) {
        Dialog(onDismissRequest = { showFontDialog = false }) {
            DialogFrame {
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
        Dialog(onDismissRequest = {
            showFastToggle = false
            browserViewModel.reapplyWebConfig()
        }) {
            DialogFrame {
                FastToggleDialogContent(onDismiss = {
                    showFastToggle = false
                    // Adblock/JS/cookie/incognito toggles take effect on live tabs.
                    browserViewModel.reapplyWebConfig()
                })
            }
        }
    }
    if (showSiteSettings) {
        Dialog(onDismissRequest = {
            showSiteSettings = false
            browserViewModel.reapplyWebConfig()
        }) {
            DialogFrame {
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
        Dialog(onDismissRequest = { showTouchAreaDialog = false }) {
            DialogFrame {
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

    if (showTtsDialog) {
        Dialog(onDismissRequest = { showTtsDialog = false }) {
            DialogFrame {
                TtsSettingDialogContent(
                    ttsViewModel = ttsViewModel,
                    onDismiss = { showTtsDialog = false },
                )
            }
        }
    }

    if (showTranslateDialog) {
        Dialog(onDismissRequest = { showTranslateDialog = false }) {
            DialogFrame {
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
            DialogFrame {
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
}
