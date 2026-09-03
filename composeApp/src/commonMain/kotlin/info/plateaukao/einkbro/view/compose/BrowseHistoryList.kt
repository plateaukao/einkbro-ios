package info.plateaukao.einkbro.view.compose

import info.plateaukao.einkbro.util.System
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Point
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.database.BookmarkManager
import info.plateaukao.einkbro.database.Record
import info.plateaukao.einkbro.database.RecordType
import info.plateaukao.einkbro.view.dialog.compose.toScreenPoint
import info.plateaukao.einkbro.util.DateFormat
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseHistoryList(
    modifier: Modifier,
    records: List<Record>,
    shouldReverse: Boolean,
    shouldShowTwoColumns: Boolean,
    bookmarkManager: BookmarkManager? = null,
    showThumbnailGrid: Boolean = false,
    onClick: (Record) -> Unit,
    onLongClick: (Record, Point) -> Unit,
    onAppendClick: (String) -> Unit = {},
) {
    if (showThumbnailGrid) {
        ThumbnailHistoryGrid(
            modifier = modifier,
            records = records,
            shouldReverse = shouldReverse,
            bookmarkManager = bookmarkManager,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Fixed(if (shouldShowTwoColumns) 2 else 1),
        reverseLayout = shouldReverse
    ) {

        itemsIndexed(records) { index, record ->
            var boxPosition = remember { mutableStateOf(Offset.Zero) }

            RecordItem(
                record = record,
                bitmap = bookmarkManager?.findFaviconBitmapBy(record.url),
                modifier = Modifier
                    .pointerInput(record) {
                        detectTapGestures(
                            onTap = { _ -> onClick(record) },
                            onLongPress = { it ->
                                onLongClick(record, it.toScreenPoint(boxPosition.value))
                            }
                        )
                    }
                    .onGloballyPositioned { boxPosition.value = it.positionOnScreen() },
                onAppendClick = onAppendClick,

                )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailHistoryGrid(
    modifier: Modifier,
    records: List<Record>,
    shouldReverse: Boolean,
    bookmarkManager: BookmarkManager? = null,
    onClick: (Record) -> Unit,
    onLongClick: (Record, Point) -> Unit,
) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 56.dp),
        reverseLayout = shouldReverse,
    ) {
        itemsIndexed(records) { _, record ->
            val boxPosition = remember { mutableStateOf(Offset.Zero) }
            ThumbnailHistoryItem(
                record = record,
                bitmap = bookmarkManager?.findFaviconBitmapBy(record.url),
                modifier = Modifier
                    .pointerInput(record) {
                        detectTapGestures(
                            onTap = { onClick(record) },
                            onLongPress = { offset ->
                                onLongClick(record, offset.toScreenPoint(boxPosition.value))
                            }
                        )
                    }
                    .onGloballyPositioned { boxPosition.value = it.positionOnScreen() },
            )
        }
    }
}

@Composable
private fun ThumbnailHistoryItem(
    modifier: Modifier,
    record: Record,
    bitmap: ImageBitmap?,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                modifier = Modifier.size(32.dp),
                bitmap = bitmap,
                contentDescription = null,
            )
        } else {
            // No favicon stored for this host: the app logo, same fallback as
            // the bookmark list, instead of the generic history clock.
            Image(
                modifier = Modifier.size(32.dp),
                painter = painterResource(Res.drawable.ic_launcher),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun RecordItem(
    modifier: Modifier,
    bitmap: ImageBitmap? = null,
    record: Record,
    onAppendClick: (String) -> Unit = {},
) {
    val timeString =
        if (record.type == RecordType.History) DateFormat.format(record.time, "MM/dd")
        else ""

    val isTypeSuggestion = record.type == RecordType.Suggestion

    Row(
        modifier = modifier
            .padding(2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            record.type == RecordType.Bookmark -> {
                Icon(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(30.dp)
                        .padding(end = 5.dp),
                    imageVector = vectorResource(Res.drawable.icon_bookmark),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground
                )
            }

            isTypeSuggestion -> {
                IconButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight()
                        .padding(0.dp),
                    onClick = { onAppendClick(record.title.orEmpty()) }
                ) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(20.dp),
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground
                    )
                }
            }

            bitmap != null -> {
                Image(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(30.dp)
                        .padding(end = 5.dp),
                    bitmap = bitmap,
                    contentDescription = null,
                )
            }

            else -> {
                // History row without a stored favicon: app logo, not the clock.
                Image(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(30.dp)
                        .padding(end = 5.dp),
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = null,
                )
            }
        }
        Column(
            Modifier
                .weight(1F)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                modifier = Modifier
                    .conditional(isTypeSuggestion) {
                        height(35.dp).padding(end = 5.dp)
                    },
                text = record.title ?: "Unknown",
                fontSize = if (isTypeSuggestion) 18.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onBackground,
            )
            if (!isTypeSuggestion) {
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1F)
                            .align(Alignment.Top),
                        text = record.url,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground,
                    )
                    // alight to end of row
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .align(Alignment.Top),
                        text = timeString,
                        textAlign = TextAlign.End,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun previewItem() {
    MyTheme {
        RecordItem(
            modifier = Modifier,
            record = Record(
                title = "Hello",
                url = "123ddddddddddddddddddddddddd",
                time = System.currentTimeMillis()
            )
        )
    }
}


@Composable
private fun previewHistoryList() {
    val list = listOf(
        Record(
            title = "Hello aaa aaa aaa aa aa aaa aa a aa a a a aa a a a a a a a a a aa a a ",
            url = "123",
            time = System.currentTimeMillis()
        ),
        Record(
            title = "Hello 2",
            url = "123 dddddddddddddddddddddddddddddddddddddddd",
            time = System.currentTimeMillis()
        ),
        Record(title = "Hello 3", url = "123", time = System.currentTimeMillis()),
    )
    MyTheme {
        BrowseHistoryList(
            modifier = Modifier,
            records = list,
            shouldReverse = true,
            shouldShowTwoColumns = true,
            onClick = {},
            onLongClick = { _, _ -> })
    }
}
