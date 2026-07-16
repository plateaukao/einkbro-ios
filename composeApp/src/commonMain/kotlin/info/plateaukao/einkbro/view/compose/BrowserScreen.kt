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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import info.plateaukao.einkbro.view.dialog.compose.TouchAreaDialogContent
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo
import info.plateaukao.einkbro.viewmodel.BrowserViewModel

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
    var showUrlInput by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showFastToggle by remember { mutableStateOf(false) }
    var showTouchAreaDialog by remember { mutableStateOf(false) }
    var touchPagingEnabled by remember { mutableStateOf(config.touch.enableTouchTurn) }

    LaunchedEffect(Unit) { browserViewModel.ensureFirstTab() }

    val engine = browserViewModel.currentEngine
    val progress by browserViewModel.progress

    fun handleToolbarAction(action: ToolbarAction) {
        when (action) {
            ToolbarAction.Back -> if (engine?.canGoBack() == true) engine.goBack()
            else browserViewModel.currentAlbum?.let { browserViewModel.closeTab(it) }

            ToolbarAction.Forward -> engine?.goForward()
            ToolbarAction.Refresh -> engine?.reload()
            ToolbarAction.PageUp -> engine?.pageUp()
            ToolbarAction.PageDown -> engine?.pageDown()
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
            MenuItemType.FontSize -> showFontDialog = true
            MenuItemType.TouchSetting -> showTouchAreaDialog = true
            MenuItemType.SaveBookmark -> {
                val album = browserViewModel.currentAlbum ?: return
                AppServices.bookmarkManager.bookmarks.add(
                    info.plateaukao.einkbro.database.Bookmark(
                        title = album.albumTitle,
                        url = browserViewModel.currentUrl.value,
                    )
                )
                EBToast.show(AppServices.context, "Bookmark saved")
            }

            MenuItemType.Quit -> onOpenCatalog()
            else -> EBToast.show(AppServices.context, "${item.name}: later phase")
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (engine != null) {
                WebViewHost(engine, Modifier.fillMaxSize())
            }

            if (touchPagingEnabled) {
                Box(
                    Modifier.align(Alignment.CenterStart).width(48.dp).fillMaxHeight(0.6f)
                        .clickable { engine?.pageUp() }
                )
                Box(
                    Modifier.align(Alignment.CenterEnd).width(48.dp).fillMaxHeight(0.6f)
                        .clickable { engine?.pageDown() }
                )
            }

            if (showOverview) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    HistoryAndTabs(
                        isHistoryOpen = false,
                        albumList = browserViewModel.albums,
                        albumFocusIndex = browserViewModel.focusIndex,
                        onTabIconClick = {},
                        onTabClick = { browserViewModel.switchTab(it); showOverview = false },
                        onTabLongClick = { browserViewModel.closeTab(it) },
                        records = browserViewModel.records.reversed(),
                        onHistoryIconClick = {},
                        onHistoryItemClick = {
                            browserViewModel.loadUrlOrSearch(it.url); showOverview = false
                        },
                        onHistoryItemLongClick = { _, _ -> },
                        addIncognitoTab = {
                            EBToast.show(AppServices.context, "Incognito: phase 4")
                        },
                        addTab = {
                            browserViewModel.newTab(BrowserViewModel.DEFAULT_HOME)
                            showOverview = false
                        },
                        closePanel = { showOverview = false },
                        onDeleteAction = { browserViewModel.records.clear() },
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
                val recordsState = remember(browserViewModel.records.size) {
                    mutableStateOf(browserViewModel.records.reversed())
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
            isIncognito = false,
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
            DialogFrame { FontDialogContent(onDismiss = { showFontDialog = false }) }
        }
    }
    if (showFastToggle) {
        Dialog(onDismissRequest = { showFastToggle = false }) {
            DialogFrame { FastToggleDialogContent(onDismiss = { showFastToggle = false }) }
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
