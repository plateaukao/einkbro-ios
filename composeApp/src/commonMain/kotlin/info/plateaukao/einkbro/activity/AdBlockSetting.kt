package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import info.plateaukao.einkbro.util.NoDimAlertDialog as AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.AdFilter
import info.plateaukao.einkbro.browser.DownloadState
import info.plateaukao.einkbro.browser.Filter
import info.plateaukao.einkbro.browser.FilterViewModel
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.settings
import info.plateaukao.einkbro.util.DateFormat
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.MyTheme
import org.jetbrains.compose.resources.stringResource

private const val FILTER_DATE_PATTERN = "yyyy-MM-dd HH:mm"

/**
 * Port of AdBlockSettingActivity: the ad-block filter-list settings screen.
 * Backed by the in-memory AdFilter/FilterViewModel stub; the add-filter
 * dialog (an Android EditText AlertDialog there) is a Compose dialog here.
 */
@Composable
fun AdBlockSettingScreen(onClose: () -> Unit = {}) {
    val viewModel = remember { AdFilter.get().viewModel }
    val dialogManager = remember { AppServices.dialogManager }
    val context = LocalContext.current
    var showAddFilterDialog by remember { mutableStateOf(false) }

    MyTheme {
        SettingsScreen(
            viewModel = viewModel,
            addFilterDialog = { showAddFilterDialog = true },
            deleteFilterDialog = { filter ->
                dialogManager.showOkCancelDialog(
                    title = "delete filter",
                    message = "Are you sure to delete this filter?",
                    okAction = {
                        viewModel.removeFilter(filter.id)
                    }
                )
            },
            onCloseAction = onClose,
        )
        if (showAddFilterDialog) {
            AddFilterDialog(
                onDismiss = { showAddFilterDialog = false },
                onAdd = { url ->
                    showAddFilterDialog = false
                    if (url.isNotBlank() &&
                        (url.startsWith("http://") || url.startsWith("https://"))
                    ) {
                        val filter = viewModel.addFilter("", url)
                        viewModel.download(filter.id)
                    } else {
                        EBToast.show(context, "invalid url")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: FilterViewModel,
    addFilterDialog: () -> Unit,
    deleteFilterDialog: (Filter) -> Unit,
    onCloseAction: () -> Unit = {},
) {
    val filters = viewModel.filters.collectAsState()

    ListScaffold(
        title = stringResource(Res.string.settings),
        onBack = onCloseAction,
        actions = {
            IconButton(onClick = { filters.value.keys.forEach { viewModel.download(it) } }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "update")
            }
            IconButton(onClick = addFilterDialog) {
                Icon(Icons.Default.Add, contentDescription = "add filter")
            }
            IconButton(onClick = { onCloseAction() }) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel")
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            LazyColumn {
                val filterList = filters.value.values.toList()
                items(filterList.size, key = { filterList[it].id }) { index ->
                    val filter = filterList[index]
                    FilterRow(filter,
                        onClick = {
                            viewModel.download(it.id)
                        },
                        onLongClick = { deleteFilterDialog(it) },
                        onToggled = { toggledFilter, enabled ->
                            viewModel.setFilterEnabled(toggledFilter.id, enabled)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterRow(
    filter: Filter,
    onClick: (Filter) -> Unit,
    onLongClick: (Filter) -> Unit,
    onToggled: (Filter, Boolean) -> Unit,
) {
    // keyed so the switch reflects fresh data when the filters flow re-emits
    val isChecked = remember(filter.isEnabled) { mutableStateOf(filter.isEnabled) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = { onClick(filter) },
                onLongClick = { onLongClick(filter) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (filter.name.isBlank()) filter.url else filter.name,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
            )
            Text(
                text = filter.url,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                color = Color.Gray
            )
            Row {
                Text(
                    modifier = Modifier.weight(1f),
                    text = when (filter.downloadState) {
                        DownloadState.ENQUEUED -> "waiting"
                        DownloadState.DOWNLOADING -> "downloading"
                        DownloadState.INSTALLING -> "installing"
                        DownloadState.FAILED -> "failed"
                        DownloadState.CANCELLED -> "cancelled"
                        else -> {
                            if (filter.hasDownloaded())
                                DateFormat.format(filter.updateTime, FILTER_DATE_PATTERN)
                            else "not downloaded"
                        }
                    },
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
                if (filter.hasDownloaded()) {
                    Text(
                        text = "filter count: ${filter.filtersCount}",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            }
        }
        Switch(
            checked = isChecked.value,
            onCheckedChange = {
                isChecked.value = it
                onToggled(filter, it)
            },
            enabled = filter.filtersCount > 0
        )
    }
}

/** Compose replacement for the Android EditText-based "add filter" dialog. */
@Composable
private fun AddFilterDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("add filter", style = MaterialTheme.typography.h6) },
        text = {
            TextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                placeholder = { Text("add filter") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(url) }) {
                Text("OK", color = MaterialTheme.colors.onBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colors.onBackground)
            }
        },
    )
}
