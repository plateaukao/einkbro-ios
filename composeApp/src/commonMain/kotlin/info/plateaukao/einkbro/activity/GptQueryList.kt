package info.plateaukao.einkbro.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.database.ChatGptQuery
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.icon_delete
import info.plateaukao.einkbro.resources.icon_exit
import info.plateaukao.einkbro.resources.icon_export
import info.plateaukao.einkbro.unit.HelperUnit
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.util.DateFormat
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.viewmodel.GptQueryViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.vectorResource
import info.plateaukao.einkbro.view.compose.ThemedDivider

// Android KeyEvent codes for the volume keys (the Activity forwarded them to
// scroll+expand the list; on iOS nothing emits, but the plumbing is kept).
private const val KEYCODE_VOLUME_UP = 24
private const val KEYCODE_VOLUME_DOWN = 25

/** Port of GptQueryListActivity: the saved GPT query/result list. */
@Composable
fun GptQueryListScreen(onClose: () -> Unit = {}) {
    val gptQueryViewModel = remember { GptQueryViewModel() }
    val context = LocalContext.current
    // Events, not state: a state write recomposed the whole screen twice per
    // press (value, then a postDelayed reset back to a sentinel).
    val volumeKeyEvents = remember { MutableSharedFlow<Int>(extraBufferCapacity = 4) }

    ListScaffold(
        title = "Gpt Results",
        onBack = onClose,
        actions = {
            // Add a button to export highlights
            IconButton(onClick = {
                // Android: file picker + BackupUnit export of the dumped html.
                EBToast.show(context, "would export GPT queries as html")
            }) {
                Icon(
                    imageVector = vectorResource(Res.drawable.icon_export),
                    contentDescription = "Export"
                )
            }
        },
    ) { _ ->
        GptQueriesScreen(
            gptQueryViewModel,
            volumeKeyEvents = volumeKeyEvents,
            onLinkClick = {
                IntentUnit.launchUrl(context, it.url)
            }
        )
    }
}

@Composable
fun GptQueriesScreen(
    gptQueryViewModel: GptQueryViewModel,
    volumeKeyEvents: Flow<Int>,
    onLinkClick: (ChatGptQuery) -> Unit,
) {
    val gptQueries by gptQueryViewModel.getGptQueries().collectAsState(emptyList())
    val listState = rememberLazyListState()
    val forceExpandIndex = remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        volumeKeyEvents.collect { keyCode ->
            when (keyCode) {
                KEYCODE_VOLUME_UP -> {
                    if (listState.firstVisibleItemIndex > 0) {
                        listState.scrollToItem(listState.firstVisibleItemIndex - 1)
                        forceExpandIndex.value = listState.firstVisibleItemIndex
                    }
                }

                KEYCODE_VOLUME_DOWN -> {
                    if (listState.firstVisibleItemIndex < gptQueries.size) {
                        listState.scrollToItem(listState.firstVisibleItemIndex + 1)
                        forceExpandIndex.value = listState.firstVisibleItemIndex
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        items(gptQueries.size, key = { gptQueries[it].id }) { index ->
            val gptQuery = gptQueries[index]
            QueryItem(
                modifier = Modifier.padding(vertical = 4.dp),
                gptQuery = gptQuery,
                forceExpand = forceExpandIndex.value == index,
                onLinkClick = { onLinkClick(gptQuery) },
                deleteQuery = { gptQueryViewModel.deleteGptQuery(gptQuery) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueryItem(
    modifier: Modifier,
    gptQuery: ChatGptQuery,
    forceExpand: Boolean = false,
    onLinkClick: () -> Unit = {},
    deleteQuery: () -> Unit,
) {
    var showResult by remember { mutableStateOf(false) }
    val queryString = remember(gptQuery.selectedText) {
        if (gptQuery.selectedText.contains("<<") &&
            gptQuery.selectedText.contains(">>")
        ) {
            HelperUnit.parseMarkdown(gptQuery.selectedText.replace("<<", "**").replace(">>", "**"))
        } else {
            AnnotatedString(gptQuery.selectedText)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.3f)),
        backgroundColor = MaterialTheme.colors.background,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { showResult = !showResult },
                    onLongClick = { deleteQuery() }
                )
                .padding(12.dp)
        ) {
            Text(
                text = queryString,
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.body1,
            )
            if (showResult || forceExpand) {
                ThemedDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
                )
                Text(
                    text = remember(gptQuery.result) { HelperUnit.parseMarkdown(gptQuery.result) },
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.body2,
                )
            }
            // metadata footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = gptQuery.model,
                    style = MaterialTheme.typography.caption.copy(
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = remember(gptQuery.date) { DateFormat.format(gptQuery.date, "MMM dd") },
                    style = MaterialTheme.typography.caption.copy(
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                    )
                )
                if (gptQuery.url.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onLinkClick() },
                        imageVector = vectorResource(Res.drawable.icon_exit),
                        contentDescription = "link",
                        tint = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { deleteQuery() },
                    imageVector = vectorResource(Res.drawable.icon_delete),
                    contentDescription = "delete",
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun PreviewQueryItem() {
    MyTheme {
        QueryItem(
            modifier = Modifier.padding(8.dp),
            gptQuery = ChatGptQuery(
                selectedText = "selected text",
                result = "result",
                date = System.currentTimeMillis(),
                url = "https://example.com",
                model = "gpt-4o",
            ),
            onLinkClick = {},
            deleteQuery = {}
        )
    }
}
