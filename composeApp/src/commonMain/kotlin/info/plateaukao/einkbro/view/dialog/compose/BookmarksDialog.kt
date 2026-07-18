package info.plateaukao.einkbro.view.dialog.compose

import android.graphics.Point
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.util.NoDimDialog as Dialog
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.database.BookmarkManager
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.drag_to_reorder
import info.plateaukao.einkbro.resources.ic_folder
import info.plateaukao.einkbro.resources.ic_launcher
import info.plateaukao.einkbro.resources.ic_grid_view
import info.plateaukao.einkbro.resources.ic_hamburger
import info.plateaukao.einkbro.resources.ic_sort
import info.plateaukao.einkbro.resources.icon_arrow_down_gest
import info.plateaukao.einkbro.resources.icon_arrow_left_gest
import info.plateaukao.einkbro.resources.icon_list
import info.plateaukao.einkbro.resources.no_bookmarks
import info.plateaukao.einkbro.unit.ViewUnit
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.getString
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.compose.NormalTextModifier
import info.plateaukao.einkbro.view.dialog.BookmarkEditContent
import info.plateaukao.einkbro.viewmodel.BookmarkViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

typealias OnBookmarkClick = (bookmark: Bookmark) -> Unit
typealias OnBookmarkLongClick = (bookmark: Bookmark, point: Point) -> Unit
typealias OnBookmarkIconClick = (bookmark: Bookmark) -> Unit

/**
 * Bookmark browser dialog content. On Android this is BookmarksDialogFragment;
 * the fragment plumbing (lifecycleScope job, dialog window, nested fragments
 * for the context menu / edit dialog) is replaced by local Compose state.
 */
@Composable
fun BookmarksDialogContent(
    bookmarkViewModel: BookmarkViewModel = remember { BookmarkViewModel(AppServices.bookmarkManager) },
    gotoUrlAction: (String) -> Unit = {},
    bookmarkIconClickAction: (title: String, url: String, isForeground: Boolean) -> Unit = { _, _, _ -> },
    splitScreenAction: (String) -> Unit = {},
    closeAction: () -> Unit = {},
) {
    val config = AppServices.config
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showTwoColumn = ViewUnit.isWideLayout(context)

    val bookmarks = remember { mutableStateOf(emptyList<Bookmark>()) }
    val shouldShowDragHandle = remember { mutableStateOf(false) }
    val isGridView = remember { mutableStateOf(config.ui.isBookmarkGridView) }
    val contextMenuBookmark = remember { mutableStateOf<Bookmark?>(null) }
    val editBookmark = remember { mutableStateOf<Bookmark?>(null) }

    LaunchedEffect(bookmarkViewModel) {
        bookmarkViewModel.uiState.collect { bookmarks.value = it }
    }

    // Mirrors the fragment's onDestroy: persist an in-progress sort and
    // reset the folder stack when the dialog leaves composition.
    DisposableEffect(bookmarkViewModel) {
        onDispose {
            if (shouldShowDragHandle.value) {
                bookmarkViewModel.updateBookmarksOrder(bookmarks.value)
            }
            bookmarkViewModel.toRootFolder()
        }
    }

    DialogPanel(
        folder = bookmarkViewModel.currentFolder.value,
        inSortMode = shouldShowDragHandle.value,
        isGridView = isGridView.value,
        upParentAction = { bookmarkViewModel.outOfFolder() },
        toggleGridViewAction = {
            isGridView.value = !isGridView.value
            config.ui.isBookmarkGridView = isGridView.value
        },
        reorderBookmarkAction = {
            if (shouldShowDragHandle.value) {
                // Exiting sort mode - persist the current order
                bookmarkViewModel.updateBookmarksOrder(bookmarks.value)
            }
            shouldShowDragHandle.value = !shouldShowDragHandle.value
            if (shouldShowDragHandle.value) {
                EBToast.show(context, context.getString(Res.string.drag_to_reorder))
            }
        },
        closeAction = closeAction,
    ) {
        if (bookmarks.value.isEmpty()) {
            Text(
                modifier = NormalTextModifier,
                text = context.getString(Res.string.no_bookmarks),
                color = MaterialTheme.colors.onBackground
            )
        } else {
            BookmarkList(
                bookmarks = bookmarks.value,
                bookmarkViewModel = bookmarkViewModel,
                showTwoColumn = showTwoColumn,
                isGridView = isGridView.value,
                shouldReverse = !config.ui.isToolbarOnTop,
                shouldShowDragHandle = shouldShowDragHandle.value,
                onItemMoved = { from, to ->
                    bookmarks.value =
                        if (!isGridView.value && showTwoColumn) moveItemInTwoColumns(bookmarks.value, from, to)
                        else bookmarks.value.toMutableList().apply { add(to, removeAt(from)) }
                },
                onBookmarkClick = {
                    if (!it.isDirectory) {
                        gotoUrlAction(it.url)
                        config.addRecentBookmark(it)
                        closeAction()
                    } else {
                        bookmarkViewModel.intoFolder(it)
                    }
                },
                onBookmarkIconClick = {
                    if (!it.isDirectory) bookmarkIconClickAction(
                        it.title,
                        it.url,
                        true
                    ); closeAction()
                },
                onBookmarkLongClick = { bookmark, _ ->
                    // Android anchors the context-menu window at the touch
                    // point; CMP dialogs are centered, so the point is unused.
                    contextMenuBookmark.value = bookmark
                }
            )
        }
    }

    contextMenuBookmark.value?.let { bookmark ->
        Dialog(onDismissRequest = { contextMenuBookmark.value = null }) {
            BookmarkContextMenuScreen(bookmark = bookmark) { itemType ->
                contextMenuBookmark.value = null
                when (itemType) {
                    ContextMenuItemType.NewTabForeground -> {
                        bookmarkIconClickAction(bookmark.title, bookmark.url, true)
                        closeAction()
                    }

                    ContextMenuItemType.NewTabBackground -> {
                        bookmarkIconClickAction(bookmark.title, bookmark.url, false)
                        closeAction()
                    }

                    ContextMenuItemType.SplitScreen -> {
                        splitScreenAction(bookmark.url)
                        closeAction()
                    }

                    ContextMenuItemType.Edit -> editBookmark.value = bookmark

                    ContextMenuItemType.Delete -> coroutineScope.launch {
                        bookmarkViewModel.deleteBookmark(bookmark)
                    }

                    else -> Unit
                }
            }
        }
    }

    editBookmark.value?.let { bookmark ->
        BookmarkEditContent(
            bookmarkViewModel = bookmarkViewModel,
            bookmark = bookmark,
            okAction = { editBookmark.value = null },
            dismissAction = { editBookmark.value = null },
        )
    }
}

@Composable
fun DialogPanel(
    folder: Bookmark,
    inSortMode: Boolean = false,
    isGridView: Boolean = false,
    upParentAction: (Bookmark) -> Unit,
    closeAction: () -> Unit,
    reorderBookmarkAction: () -> Unit,
    toggleGridViewAction: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.weight(1F, fill = false)) {
            content()
        }
        Divider(thickness = 1.dp, color = MaterialTheme.colors.onBackground)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (folder.id != 0) {
                ActionIcon(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    iconResId = Res.drawable.icon_arrow_left_gest,
                    action = { upParentAction(folder) }
                )
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
            Text(
                if (folder.id == 0) "" else folder.title,
                Modifier
                    .weight(1F)
                    .padding(horizontal = 5.dp)
                    .align(Alignment.CenterVertically)
                    .clickable { if (folder.id != 0) upParentAction(folder) },
                color = MaterialTheme.colors.onBackground
            )
            ActionIcon(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 5.dp),
                iconResId = if (inSortMode) Res.drawable.icon_list else Res.drawable.ic_sort,
                action = { reorderBookmarkAction() },
            )
            ActionIcon(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 5.dp),
                iconResId = if (isGridView) Res.drawable.ic_hamburger else Res.drawable.ic_grid_view,
                action = toggleGridViewAction
            )
            ActionIcon(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 5.dp),
                iconResId = Res.drawable.icon_arrow_down_gest,
                action = closeAction
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkList(
    bookmarks: List<Bookmark>,
    bookmarkViewModel: BookmarkViewModel,
    showTwoColumn: Boolean = false,
    isGridView: Boolean = false,
    shouldReverse: Boolean = true,
    shouldShowDragHandle: Boolean = false,
    onItemMoved: (from: Int, to: Int) -> Unit,
    onBookmarkClick: OnBookmarkClick,
    onBookmarkIconClick: OnBookmarkIconClick,
    onBookmarkLongClick: OnBookmarkLongClick,
) {
    // key() forces full recreation (no animation) when folder or view mode changes
    key(bookmarkViewModel.currentFolder.value.id, isGridView) {
        val lazyGridState = rememberLazyGridState()
        val reorderableLazyGridState =
            rememberReorderableLazyGridState(lazyGridState) { from, to ->
                onItemMoved(from.index, to.index)
            }

        // Use RTL layout direction for grid mode so items fill right-to-left within each row
        CompositionLocalProvider(
            LocalLayoutDirection provides if (isGridView) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        LazyVerticalGrid(
            modifier = Modifier.wrapContentHeight(),
            state = lazyGridState,
            columns = if (isGridView) GridCells.Adaptive(73.dp) else GridCells.Fixed(if (showTwoColumn) 2 else 1),
            reverseLayout = shouldReverse
        ) {
            itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.id }) { _, bookmark ->
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                // for getting long click point
                var longClickPosition = remember { mutableStateOf(Offset.Zero) }
                var boxPosition = remember { mutableStateOf(Offset.Zero) }


                ReorderableItem(reorderableLazyGridState, key = bookmark.id) { isDragging ->
                    if (isGridView) {
                        // Restore LTR for grid item content (text, icons)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            BookmarkGridItem(
                                bookmark = bookmark,
                                bitmap = remember(bookmark.url) { bookmarkViewModel.getFavicon(bookmark) },
                                isPressed = isPressed || isDragging,
                                shouldShowDragHandle = shouldShowDragHandle,
                                iconDragModifier = if (shouldShowDragHandle) Modifier.draggableHandle() else Modifier,
                                modifier = Modifier.then(
                                    if (shouldShowDragHandle) {
                                        Modifier
                                            .longPressDraggableHandle()
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                            ) { onBookmarkClick(bookmark) }
                                    } else {
                                        Modifier
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { onBookmarkClick(bookmark) },
                                                    onLongPress = { offset ->
                                                        longClickPosition.value = offset
                                                        onBookmarkLongClick(
                                                            bookmark,
                                                            offset.toScreenPoint(boxPosition.value)
                                                        )
                                                    }
                                                )
                                            }
                                            .onGloballyPositioned {
                                                boxPosition.value = it.positionOnScreen()
                                            }
                                    }
                                ),
                            )
                        }
                    } else {
                        BookmarkItem(
                            bookmark = bookmark,
                            bitmap = remember(bookmark.url) { bookmarkViewModel.getFavicon(bookmark) },
                            isPressed = isPressed || isDragging,
                            shouldShowDragHandle = shouldShowDragHandle,
                            dragModifier = Modifier.draggableHandle(),
                            modifier = Modifier.then(
                                if (shouldShowDragHandle) {
                                    Modifier
                                        .longPressDraggableHandle()
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                        ) { onBookmarkClick(bookmark) }
                                } else {
                                    Modifier
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = { offset -> onBookmarkClick(bookmark) },
                                                onLongPress = { offset ->
                                                    longClickPosition.value = offset
                                                    onBookmarkLongClick(
                                                        bookmark,
                                                        offset.toScreenPoint(boxPosition.value)
                                                    )
                                                }
                                            )
                                        }
                                        .onGloballyPositioned {
                                            boxPosition.value = it.positionOnScreen()
                                        }
                                }
                            ),
                            iconClick = {
                                if (!bookmark.isDirectory) onBookmarkIconClick(bookmark)
                                else onBookmarkClick(bookmark)
                            }
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun BookmarkItem(
    modifier: Modifier,
    bitmap: ImageBitmap? = null,
    isPressed: Boolean = false,
    shouldShowDragHandle: Boolean = false,
    bookmark: Bookmark,
    dragModifier: Modifier = Modifier,
    iconClick: () -> Unit,
) {
    val borderWidth = if (isPressed) 1.dp else (-1).dp

    Row(
        modifier = modifier
            .height(54.dp)
            .padding(4.dp)
            .border(borderWidth, MaterialTheme.colors.onBackground, RoundedCornerShape(7.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        if (shouldShowDragHandle) {
            Icon(
                modifier = dragModifier.padding(8.dp),
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground
            )
        }
        if (bitmap != null) {
            Image(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(36.dp)
                    .padding(end = 5.dp)
                    .clickable { iconClick() },
                bitmap = bitmap,
                contentDescription = null,
            )
        } else if (bookmark.isDirectory) {
            ActionIcon(
                modifier = Modifier.align(Alignment.CenterVertically),
                iconResId = Res.drawable.ic_folder,
                action = iconClick
            )
        } else {
            // Same fallback as Android: the app launcher icon.
            Image(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(36.dp)
                    .padding(end = 5.dp)
                    .clickable { iconClick() },
                painter = org.jetbrains.compose.resources.painterResource(
                    Res.drawable.ic_launcher
                ),
                contentDescription = null,
            )
        }
        Text(
            modifier = Modifier
                .weight(1.0f)
                .align(Alignment.CenterVertically),
            text = bookmark.title,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

@Composable
fun BookmarkGridItem(
    modifier: Modifier,
    bitmap: ImageBitmap? = null,
    isPressed: Boolean = false,
    shouldShowDragHandle: Boolean = false,
    iconDragModifier: Modifier = Modifier,
    bookmark: Bookmark,
) {
    val borderWidth = if (isPressed) 1.dp else (-1).dp

    Column(
        modifier = modifier
            .padding(4.dp)
            .border(borderWidth, MaterialTheme.colors.onBackground, RoundedCornerShape(7.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (bitmap != null) {
            Image(
                modifier = Modifier
                    .then(iconDragModifier)
                    .size(48.dp)
                    .padding(4.dp),
                bitmap = bitmap,
                contentDescription = null,
            )
        } else if (bookmark.isDirectory) {
            Icon(
                modifier = Modifier
                    .then(iconDragModifier)
                    .size(48.dp)
                    .padding(4.dp),
                imageVector = vectorResource(Res.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
            )
        } else {
            // Same fallback as Android: the app launcher icon.
            Image(
                modifier = Modifier
                    .then(iconDragModifier)
                    .size(48.dp)
                    .padding(4.dp),
                painter = org.jetbrains.compose.resources.painterResource(
                    Res.drawable.ic_launcher
                ),
                contentDescription = null,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = bookmark.title,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PreviewBookmarkList() {
    MyTheme {
        BookmarkList(
            bookmarks = listOf(Bookmark("test 1", "https://www.google.com", false)),
            bookmarkViewModel = remember { BookmarkViewModel(info.plateaukao.einkbro.AppServices.bookmarkManager) },
            showTwoColumn = true,
            shouldReverse = true,
            shouldShowDragHandle = false,
            onItemMoved = { _, _ -> },
            onBookmarkClick = {},
            onBookmarkIconClick = {},
            onBookmarkLongClick = { _, _ -> }
        )
    }
}

// preview dialog panel
@Composable
fun PreviewDialogPanel() {
    MyTheme {
        DialogPanel(
            folder = Bookmark("test 1", "https://www.google.com", false),
            inSortMode = false,
            upParentAction = {},
            closeAction = {},
            reorderBookmarkAction = {},
        ) {
            BookmarkList(
                bookmarks = listOf(Bookmark("test 1", "https://www.google.com", false)),
                bookmarkViewModel = remember { BookmarkViewModel(info.plateaukao.einkbro.AppServices.bookmarkManager) },
                showTwoColumn = true,
                shouldReverse = true,
                shouldShowDragHandle = false,
                onItemMoved = { _, _ -> },
                onBookmarkClick = {},
                onBookmarkIconClick = {},
                onBookmarkLongClick = { _, _ -> }
            )
        }
    }
}

private fun <T> moveItemInTwoColumns(
    originalList: List<T>,
    fromIndex: Int,
    toIndex: Int,
): List<T> {
    // Divide the original list into two lists: odd-positioned and even-positioned items
    val evenList = originalList.filterIndexed { index, _ -> index % 2 == 0 }.toMutableList()
    val oddList = originalList.filterIndexed { index, _ -> index % 2 != 0 }.toMutableList()

    if (fromIndex < 0 || fromIndex >= originalList.size || toIndex < 0 || toIndex >= originalList.size) {
        return originalList
    }

    val fromItem = if (fromIndex.isEven()) {
        evenList.removeAt(fromIndex / 2)
    } else {
        oddList.removeAt(fromIndex / 2)
    }

    // move item to the target position
    if (toIndex.isEven()) {
        evenList.add(toIndex / 2, fromItem)
    } else {
        oddList.add(toIndex / 2, fromItem)
    }

    // Ensure both lists are balanced (list2 can have up to 2 more items than list1)
    while (oddList.size > evenList.size + 1) {
        evenList.add(oddList.removeAt(oddList.lastIndex))
    }

    while (evenList.size > oddList.size + 1) {
        oddList.add(evenList.removeAt(evenList.lastIndex))
    }

    // Merge the lists into one
    val resultList = mutableListOf<T>()
    val maxSize = maxOf(evenList.size, oddList.size)

    for (i in 0 until maxSize) {
        if (i < evenList.size) resultList.add(evenList[i])
        if (i < oddList.size) resultList.add(oddList[i])
    }

    return resultList
}

private fun Int.isEven(): Boolean = this % 2 == 0
