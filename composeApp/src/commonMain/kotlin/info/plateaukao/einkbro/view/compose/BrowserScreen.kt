package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
import info.plateaukao.einkbro.activity.SettingsScreen
import info.plateaukao.einkbro.browser.WebViewHost
import info.plateaukao.einkbro.catalog.DialogFrame
import info.plateaukao.einkbro.preference.toggle
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.dialog.compose.BookmarksDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FastToggleDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.MenuDialogContent
import info.plateaukao.einkbro.view.dialog.compose.MenuItemType
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TouchAreaDialogContent
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo
import info.plateaukao.einkbro.viewmodel.BrowserViewModel
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
    var touchPagingEnabled by remember { mutableStateOf(config.touch.enableTouchTurn) }
    val scope = rememberCoroutineScope()

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

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
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
}
