package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.database.SavedPage
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.icon_delete
import info.plateaukao.einkbro.resources.no_saved_pages
import info.plateaukao.einkbro.resources.saved_pages
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.util.DateFormat
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.resolveStoredPath
import info.plateaukao.einkbro.view.compose.EmptyListPlaceholder
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.viewmodel.SavedPageViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** Port of SavedPagesActivity: the saved-pages (offline archive) list. */
@Composable
fun SavedPagesScreen(
    onClose: () -> Unit = {},
    onOpenPage: ((SavedPage) -> Unit)? = null,
) {
    val viewModel = remember { SavedPageViewModel() }
    val context = LocalContext.current

    ListScaffold(
        title = stringResource(Res.string.saved_pages),
        onBack = onClose,
    ) { innerPadding ->
        SavedPagesList(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            onPageClick = { savedPage ->
                if (onOpenPage != null) {
                    onOpenPage(savedPage)
                } else {
                    // Fallback (e.g. from the catalog) — hand off to the system opener.
                    IntentUnit.launchUrl(
                        context, "file://" + resolveStoredPath(savedPage.filePath),
                    )
                }
            },
            onPageDelete = { savedPage ->
                viewModel.deleteSavedPage(savedPage)
            }
        )
    }
}

@Composable
fun SavedPagesList(
    modifier: Modifier = Modifier,
    viewModel: SavedPageViewModel,
    onPageClick: (SavedPage) -> Unit,
    onPageDelete: (SavedPage) -> Unit,
) {
    val savedPages by viewModel.getAllSavedPages().collectAsState(emptyList())

    if (savedPages.isEmpty()) {
        EmptyListPlaceholder(stringResource(Res.string.no_saved_pages))
    } else {
        LazyColumn(
            modifier = modifier.padding(10.dp),
        ) {
            items(savedPages.size, key = { savedPages[it].id }) { index ->
                val savedPage = savedPages[index]
                SavedPageItem(
                    savedPage = savedPage,
                    onClick = { onPageClick(savedPage) },
                    onDelete = { onPageDelete(savedPage) },
                )
                if (index < savedPages.lastIndex) Divider(thickness = 1.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedPageItem(
    savedPage: SavedPage,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete,
            )
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = savedPage.title,
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.body1,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = savedPage.url,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.caption,
                maxLines = 1,
            )
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = DateFormat.format(savedPage.savedAt, "yyyy-MM-dd"),
                style = MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                ),
            )
            Icon(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clickable { onDelete() },
                imageVector = vectorResource(Res.drawable.icon_delete),
                contentDescription = "delete",
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}
