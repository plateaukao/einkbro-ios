package info.plateaukao.einkbro.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.database.Record
import info.plateaukao.einkbro.database.RecordType
import info.plateaukao.einkbro.unit.ShareUtil
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.Album
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.AutoCompleteTextField
import info.plateaukao.einkbro.view.compose.BrowseHistoryList
import info.plateaukao.einkbro.view.compose.ComposedSearchBar
import info.plateaukao.einkbro.view.compose.ComposedToolbar
import info.plateaukao.einkbro.view.compose.HistoryAndTabs
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo

private fun now() = System.currentTimeMillis()

fun sampleRecords(): List<Record> = listOf(
    Record("EinkBro - E Ink browser", "https://github.com/plateaukao/einkbro", now() - 60_000),
    Record("Wikipedia", "https://wikipedia.org", now() - 3_600_000),
    Record("Hacker News", "https://news.ycombinator.com", now() - 86_400_000),
    Record("A very long page title that should ellipsize somewhere on narrow screens", "https://example.com/long/path/article", now() - 172_800_000),
    Record("Kotlin Multiplatform", "https://kotlinlang.org", now() - 259_200_000, RecordType.Bookmark),
)

fun sampleAlbums(): List<Album> = listOf(
    Album("EinkBro on GitHub", "https://github.com/plateaukao/einkbro").apply { isLoaded = true },
    Album("Wikipedia", "https://wikipedia.org").apply { isLoaded = true },
    Album("New tab", "about:blank"),
)

private fun demoToolbarActionInfos(): List<ToolbarActionInfo> =
    ToolbarAction.defaultActionsForPhone.map { ToolbarActionInfo(it, false) }

val foundationSection = CatalogSection(
    title = "Browser Chrome",
    entries = listOf(
        CatalogEntry("Toolbar (bottom)", "Main browser toolbar, default phone actions") { _ ->
            val albums = remember { mutableStateOf(sampleAlbums()) }
            val focus = remember { mutableStateOf(0) }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                ComposedToolbar(
                    showTabs = false,
                    toolbarActionInfos = demoToolbarActionInfos(),
                    title = "EinkBro — E Ink browser",
                    tabCount = "2",
                    pageInfo = "3/12",
                    isIncognito = false,
                    onIconClick = { EBToast.show(null, it.name) },
                    albumList = albums,
                    albumFocusIndex = focus,
                    onAlbumClick = { EBToast.show(null, "tab: ${it.albumTitle}") },
                    onAlbumLongClick = { EBToast.show(null, "close tab: ${it.albumTitle}") },
                )
            }
        },
        CatalogEntry("Toolbar (with tab bar)", "Toolbar with the tab strip visible") { _ ->
            val albums = remember { mutableStateOf(sampleAlbums()) }
            val focus = remember { mutableStateOf(1) }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                ComposedToolbar(
                    showTabs = true,
                    toolbarActionInfos = demoToolbarActionInfos(),
                    title = "Wikipedia",
                    tabCount = "3",
                    pageInfo = "1/4",
                    isIncognito = true,
                    onIconClick = { EBToast.show(null, it.name) },
                    albumList = albums,
                    albumFocusIndex = focus,
                    onAlbumClick = { EBToast.show(null, "tab: ${it.albumTitle}") },
                    onAlbumLongClick = { EBToast.show(null, "close tab: ${it.albumTitle}") },
                )
            }
        },
        CatalogEntry("Tabs + History overview", "Tab previews and browsing history panel") { onClose ->
            val albums = remember { mutableStateOf(sampleAlbums()) }
            val focus = remember { mutableStateOf(0) }
            HistoryAndTabs(
                isHistoryOpen = true,
                albumList = albums,
                albumFocusIndex = focus,
                onTabIconClick = {},
                onTabClick = { EBToast.show(null, "tab: ${it.albumTitle}") },
                onTabLongClick = {},
                records = sampleRecords(),
                onHistoryIconClick = {},
                onHistoryItemClick = { EBToast.show(null, it.url) },
                onHistoryItemLongClick = { _, _ -> },
                addIncognitoTab = { EBToast.show(null, "new incognito tab") },
                addTab = { EBToast.show(null, "new tab") },
                closePanel = onClose,
                onDeleteAction = {},
                onCloseAllTabs = {},
                launchNewBrowserAction = {},
            )
        },
        CatalogEntry("Browsing history list", "History records with favicon fallbacks") { _ ->
            Surface(color = MaterialTheme.colors.background) {
                BrowseHistoryList(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    records = sampleRecords(),
                    shouldReverse = false,
                    shouldShowTwoColumns = false,
                    onClick = { EBToast.show(null, it.url) },
                    onLongClick = { _, _ -> },
                )
            }
        },
        CatalogEntry("In-page search bar", "Find-on-page toolbar") { onClose ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                ComposedSearchBar(
                    focusRequester = remember { FocusRequester() },
                    onTextChanged = {},
                    onCloseClick = onClose,
                    onUpClick = { EBToast.show(null, "prev: $it") },
                    onDownClick = { EBToast.show(null, "next: $it") },
                )
            }
        },
        CatalogEntry("URL input + suggestions", "Address bar with history/bookmark suggestions") { onClose ->
            val text = remember { mutableStateOf(TextFieldValue("wiki")) }
            val records = remember { mutableStateOf(sampleRecords()) }
            Box(Modifier.fillMaxSize()) {
                AutoCompleteTextField(
                    focusRequester = remember { FocusRequester() },
                    text = text,
                    recordList = records,
                    hasCopiedText = true,
                    onTextSubmit = { EBToast.show(null, "go: $it") },
                    onTextChange = {},
                    onPasteClick = { ShareUtil.copyToClipboard(info.plateaukao.einkbro.AppServices.context, "paste") },
                    closeAction = onClose,
                    onRecordClick = { EBToast.show(null, it.url) },
                )
            }
        },
    ),
)
