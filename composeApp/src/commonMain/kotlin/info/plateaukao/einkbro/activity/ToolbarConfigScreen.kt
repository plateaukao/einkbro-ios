package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ToolbarPosition
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.reader_toolbar
import info.plateaukao.einkbro.resources.toolbars
import info.plateaukao.einkbro.unit.ViewUnit
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.compose.ComposedIconBar
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.compose.ReorderableComposedIconBar
import info.plateaukao.einkbro.view.compose.ReorderableComposedIconColumn
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.view.toolbaricons.ToolbarActionInfo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Toolbar icon arrangement screen. On Android this is ToolbarConfigActivity;
 * the activity chrome (window insets, intent extra, finish()) is replaced by
 * [onClose] and an isReaderMode parameter.
 */
@Composable
fun ToolbarConfigScreen(
    onClose: () -> Unit = {},
    isReaderMode: Boolean = false,
) {
    val config = AppServices.config
    val iconEnums = if (isReaderMode) config.ui.readerToolbarActions else config.ui.toolbarActions
    val toolbarActionInfoList = iconEnums.toToolbarActionInfoList()

    MyTheme {
        // Saveable so an unsaved arrangement survives rotation.
        val list = rememberSaveable(
            stateSaver = listSaver(
                save = { infos -> infos.map { it.toolbarAction.ordinal } },
                restore = { saved ->
                    saved.map { ToolbarActionInfo(ToolbarAction.fromOrdinal(it), false) }
                },
            )
        ) { mutableStateOf(toolbarActionInfoList) }
        val topBar: @Composable () -> Unit = {
            TopAppBar(
                title = {
                    Text(text = stringResource(if (isReaderMode) Res.string.reader_toolbar else Res.string.toolbars))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                    IconButton(onClick = {
                        if (isReaderMode) {
                            config.ui.readerToolbarActions = list.value.map { it.toolbarAction }
                        } else {
                            config.ui.toolbarActions = list.value.map { it.toolbarAction }
                        }
                        onClose()
                    }) {
                        Icon(Icons.Filled.Done, contentDescription = null)
                    }
                },
            )
        }
        // Keep the top bar clear of the iOS status bar so its buttons are tappable
        // (the Android activity hides the status bar instead).
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ToolbarConfigPanel(
                list = list,
                isVerticalPreview = config.ui.isVerticalToolbar,
                isPreviewOnRight = config.ui.toolbarPosition == ToolbarPosition.Right,
                topBar = topBar,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarConfigPanel(
    list: MutableState<List<ToolbarActionInfo>>,
    isVerticalPreview: Boolean = false,
    isPreviewOnRight: Boolean = false,
    topBar: @Composable () -> Unit = {},
) {
    val isLandscape = ViewUnit.isLandscape(LocalContext.current)

    var highlightedAction by remember { mutableStateOf<ToolbarAction?>(null) }
    LaunchedEffect(highlightedAction) {
        if (highlightedAction != null) {
            delay(1000)
            highlightedAction = null
        }
    }

    val onAddAction: (ToolbarActionInfo) -> Unit = { info ->
        list.value = list.value.toMutableList().apply {
            add(size / 2, info)
        }
        highlightedAction = info.toolbarAction
    }

    val onRemoveAction: (ToolbarAction) -> Unit = { action ->
        if (action != ToolbarAction.Settings) {
            list.value = list.value.toMutableList().apply {
                val info = find { it.toolbarAction == action }
                remove(info)
            }
        }
    }

    if (isVerticalPreview) {
        val previewBar: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(50.dp)
                    .border(1.dp, MaterialTheme.colors.onBackground),
            ) {
                ReorderableComposedIconColumn(
                    list = list,
                    title = "Toolbar Configuration",
                    tabCount = "7",
                    pageInfo = "4/21",
                    onClick = onRemoveAction,
                    highlightedAction = highlightedAction,
                )
            }
        }
        val availableActions: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                topBar()
                Text(
                    modifier = Modifier.padding(start = 18.dp, top = 5.dp, bottom = 5.dp),
                    text = "Available Actions",
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.h6
                )
                Text(
                    modifier = Modifier.padding(start = 18.dp, bottom = 5.dp),
                    text = "click icon to add; click preview to remove; long click to reorder",
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.caption
                )
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    columns = GridCells.Adaptive(84.dp),
                ) {
                    val selectedActions = list.value.map { it.toolbarAction }
                    val otherActionInfos = ToolbarAction.entries
                        .filter { it !in selectedActions }
                        .toToolbarActionInfoList()
                    itemsIndexed(otherActionInfos) { index, info ->
                        AvailableActionItem(info) { onAddAction(info) }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            if (isPreviewOnRight) {
                Box(modifier = Modifier.weight(1f)) { availableActions() }
                previewBar()
            } else {
                previewBar()
                Box(modifier = Modifier.weight(1f)) { availableActions() }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            HorizontalConfigContent(list, isLandscape, onAddAction, onRemoveAction, highlightedAction)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalConfigContent(
    list: MutableState<List<ToolbarActionInfo>>,
    isLandscape: Boolean,
    onAddAction: (ToolbarActionInfo) -> Unit,
    onRemoveAction: (ToolbarAction) -> Unit,
    highlightedAction: ToolbarAction?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, start = 1.dp, end = 1.dp, bottom = 50.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        modifier = Modifier.padding(start = 10.dp, bottom = 5.dp),
                        text = "Available Actions",
                        color = MaterialTheme.colors.onBackground,
                        style = MaterialTheme.typography.h6
                    )
                    if (isLandscape) {
                        Text(
                            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                            text = "click icon to add it to the toolbar",
                            color = MaterialTheme.colors.onBackground,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
                if (!isLandscape) {
                    Text(
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                        text = "click icon to add it to the toolbar",
                        color = MaterialTheme.colors.onBackground,
                        style = MaterialTheme.typography.caption
                    )
                }
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxWidth(),
                    columns = GridCells.Adaptive(84.dp),
                ) {
                    val selectedActions = list.value.map { it.toolbarAction }
                    val otherActionInfos = ToolbarAction.entries
                        .filter { it !in selectedActions }
                        .toToolbarActionInfoList()
                    itemsIndexed(otherActionInfos) { index, info ->
                        AvailableActionItem(info) { onAddAction(info) }
                    }
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    modifier = Modifier.padding(10.dp),
                    text = "Preview",
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.h6
                )
                if (isLandscape) {
                    Text(
                        modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                        text = "click icon to remove it from the toolbar; long click to drag icon to reorder",
                        color = MaterialTheme.colors.onBackground,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
            if (!isLandscape) {
                Text(
                    modifier = Modifier.padding(start = 10.dp, bottom = 20.dp),
                    text = "click icon to remove it from the toolbar; long click to drag icon to reorder",
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.caption
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colors.onBackground),
                contentAlignment = Alignment.CenterEnd
            ) {
                ReorderableComposedIconBar(
                    list = list,
                    title = "Toolbar Configuration",
                    tabCount = "7",
                    pageInfo = "4/21",
                    onClick = onRemoveAction,
                    highlightedAction = highlightedAction,
                )
            }
        }
}

@Composable
private fun AvailableActionItem(info: ToolbarActionInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = info.toolbarAction.imageVector
                ?: info.toolbarAction.iconResId?.let { vectorResource(it) }
                ?: Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .padding(horizontal = 6.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colors.onBackground
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            text = stringResource(info.toolbarAction.titleResId),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colors.onBackground
        )
    }
}

private fun List<ToolbarAction>.toToolbarActionInfoList(): List<ToolbarActionInfo> =
    this.map { ToolbarActionInfo(it, false) }

@Composable
fun PreviewToolbarConfigPanel() {
    MyTheme {
        val toolbarActionInfoList = ToolbarAction.defaultActions.toToolbarActionInfoList()
        var list = remember { mutableStateOf(toolbarActionInfoList) }
        ToolbarConfigPanel(
            list = list,
        )
    }
}

@Composable
fun PreviewToolbar() {
    MyTheme {
        val toolbarActionInfoList = listOf(ToolbarAction.Time).toToolbarActionInfoList()
        ComposedIconBar(
            toolbarActionInfos = toolbarActionInfoList,
            title = "Toolbar Configuration",
            tabCount = "7",
            pageInfo = "4/21",
            isIncognito = false,
            onClick = { },
        )
    }
}
