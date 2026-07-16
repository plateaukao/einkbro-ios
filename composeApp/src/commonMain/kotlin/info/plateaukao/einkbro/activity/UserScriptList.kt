package info.plateaukao.einkbro.activity

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.database.UserScript
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.back
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.menu_edit
import info.plateaukao.einkbro.resources.setting_title_userscripts
import info.plateaukao.einkbro.resources.userscript_add
import info.plateaukao.einkbro.resources.userscript_browse
import info.plateaukao.einkbro.resources.userscript_code
import info.plateaukao.einkbro.resources.userscript_empty
import info.plateaukao.einkbro.resources.userscript_fetch
import info.plateaukao.einkbro.resources.userscript_install_from_url
import info.plateaukao.einkbro.resources.userscript_no_update_source
import info.plateaukao.einkbro.resources.userscript_up_to_date
import info.plateaukao.einkbro.resources.userscript_update
import info.plateaukao.einkbro.resources.userscript_update_failed
import info.plateaukao.einkbro.resources.userscript_updated
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.userscript.UpdateResult
import info.plateaukao.einkbro.userscript.UserScriptManager
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.blockingString
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.MyTheme
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val GREASY_FORK_URL = "https://greasyfork.org/"

/**
 * Port of UserScriptListActivity: the userscript manager list with enable
 * toggles, update checks, and the add/edit script dialog. Network fetches are
 * simulated with a canned userscript body.
 */
@Composable
fun UserScriptListScreen(
    installUrl: String? = null,
    onClose: () -> Unit = {},
) {
    val userScriptManager = remember { AppServices.userScriptManager }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scripts = remember { mutableStateListOf<UserScript>() }
    val updatingIds = remember { mutableStateListOf<Long>() }
    // Saveable so an in-progress edit survives configuration changes.
    var editing by rememberSaveable(stateSaver = userScriptSaver) {
        mutableStateOf<UserScript?>(null)
    }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editorSourceUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun refresh() {
        coroutineScope.launch {
            userScriptManager.reload()
            val list = userScriptManager.scripts.map { it.script }
            scripts.clear()
            scripts.addAll(list)
        }
    }

    var fetchingUrl by remember { mutableStateOf(installUrl) }

    LaunchedEffect(Unit) {
        refresh()
        // If launched with a script to install (from a .user.js URL), fetch it and
        // open the editor; fetchingUrl drives the progress indicator meanwhile.
        if (installUrl != null) {
            coroutineScope.launch {
                val fetched = fetchScript(installUrl)
                fetchingUrl = null
                if (fetched != null) {
                    editing = UserScript(name = "", code = fetched, sourceUrl = installUrl)
                    editorSourceUrl = installUrl
                    showEditor = true
                } else {
                    EBToast.show(context, "load error")
                }
            }
        }
    }

    MyTheme {
        ListScaffold(
            title = stringResource(Res.string.setting_title_userscripts),
            onBack = onClose,
            actions = {
                IconButton(onClick = {
                    IntentUnit.launchUrl(context, GREASY_FORK_URL)
                }) {
                    Icon(Icons.Outlined.Public, contentDescription = stringResource(Res.string.userscript_browse))
                }
                IconButton(onClick = {
                    editing = null
                    editorSourceUrl = null
                    showEditor = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.userscript_add))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.back))
                }
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                fetchingUrl?.let { url ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp).height(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            url,
                            maxLines = 2,
                            color = MaterialTheme.colors.onSurface,
                        )
                    }
                }
                if (scripts.isEmpty() && fetchingUrl == null) {
                    Text(
                        stringResource(Res.string.userscript_empty),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(scripts, key = { it.id }) { script ->
                        ScriptRow(
                            script = script,
                            updating = updatingIds.contains(script.id),
                            onToggle = { enabled ->
                                coroutineScope.launch {
                                    userScriptManager.setEnabled(script.id, enabled)
                                    refresh()
                                }
                            },
                            onUpdate = {
                                if (updatingIds.contains(script.id)) return@ScriptRow
                                updatingIds.add(script.id)
                                coroutineScope.launch {
                                    val result = userScriptManager.checkAndUpdate(script.id)
                                    updatingIds.remove(script.id)
                                    showUpdateResult(context, result)
                                    refresh()
                                }
                            },
                            onEdit = {
                                editing = script
                                editorSourceUrl = script.sourceUrl
                                showEditor = true
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    userScriptManager.delete(script.id)
                                    refresh()
                                }
                            },
                        )
                        Divider()
                    }
                }
            }
        }

        if (showEditor) {
            ScriptEditorDialog(
                initial = editing,
                onDismiss = { showEditor = false },
                onSaveCode = { code ->
                    showEditor = false
                    coroutineScope.launch {
                        val current = editing
                        if (current != null && current.id != 0L) {
                            userScriptManager.update(current.copy(code = code))
                        } else {
                            userScriptManager.add(code, editorSourceUrl)
                        }
                        refresh()
                    }
                },
                onFetchUrl = { url, onResult ->
                    coroutineScope.launch {
                        val fetched = fetchScript(url)
                        if (fetched != null) {
                            editorSourceUrl = url
                            onResult(fetched)
                        }
                    }
                },
            )
        }
    }
}

/** Fetches a userscript body over the network (Android used OkHttp; here Ktor). */
private suspend fun fetchScript(url: String): String? = try {
    HttpClientProvider.client.get(url).bodyAsText().ifBlank { null }
} catch (e: Exception) {
    null
}

private fun showUpdateResult(context: Context, result: UpdateResult) {
    val message = when (result) {
        is UpdateResult.Updated -> blockingString(Res.string.userscript_updated, result.to)
        UpdateResult.UpToDate -> blockingString(Res.string.userscript_up_to_date)
        UpdateResult.NoSource -> blockingString(Res.string.userscript_no_update_source)
        is UpdateResult.Failed -> blockingString(Res.string.userscript_update_failed)
    }
    EBToast.show(context, message)
}

@Composable
private fun ScriptRow(
    script: UserScript,
    updating: Boolean,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = script.enabled, onCheckedChange = onToggle)
        Spacer(Modifier.width(12.dp))
        Text(
            script.name.ifBlank { "(unnamed)" },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colors.onSurface,
        )
        IconButton(onClick = onUpdate, enabled = !updating) {
            if (updating) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.userscript_update))
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.menu_edit))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.menu_delete))
        }
    }
}

private const val EDITOR_DISPLAY_LIMIT = 10_000

// Multi-MB scripts would overflow saved-state limits on Android; above this
// size the value simply isn't saved (fetched scripts are re-fetchable via
// their sourceUrl anyway).
private const val SAVEABLE_CODE_LIMIT = 100_000

private val largeStringSaver = Saver<String, String>(
    save = { if (it.length <= SAVEABLE_CODE_LIMIT) it else null },
    restore = { it },
)

private val userScriptSaver = listSaver<UserScript?, Any?>(
    save = { script ->
        if (script == null || script.code.length > SAVEABLE_CODE_LIMIT) emptyList()
        else listOf(script.id, script.name, script.enabled, script.code, script.sourceUrl, script.order)
    },
    restore = { saved ->
        if (saved.size < 6) null
        else UserScript(
            id = saved[0] as Long,
            name = saved[1] as String,
            enabled = saved[2] as Boolean,
            code = saved[3] as String,
            sourceUrl = saved[4] as String?,
            order = saved[5] as Int,
        )
    },
)

@Composable
private fun ScriptEditorDialog(
    initial: UserScript?,
    onDismiss: () -> Unit,
    onSaveCode: (String) -> Unit,
    onFetchUrl: (String, (String) -> Unit) -> Unit,
) {
    var code by rememberSaveable(initial, stateSaver = largeStringSaver) {
        mutableStateOf(initial?.code.orEmpty())
    }
    var url by rememberSaveable { mutableStateOf("") }
    // TextField cannot handle multi-MB scripts (composition stalls the UI thread
    // indefinitely), so beyond this size show a truncated read-only preview; `code`
    // keeps the full body and is what gets saved.
    val tooLargeToEdit = code.length > EDITOR_DISPLAY_LIMIT

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(16.dp),
        ) {
            Text(
                stringResource(Res.string.setting_title_userscripts),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.userscript_install_from_url)) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { if (url.isNotBlank()) onFetchUrl(url) { code = it } }) {
                    Text(stringResource(Res.string.userscript_fetch))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = if (tooLargeToEdit) code.take(EDITOR_DISPLAY_LIMIT) + "\n..." else code,
                onValueChange = { if (!tooLargeToEdit) code = it },
                readOnly = tooLargeToEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 360.dp),
                label = { Text(stringResource(Res.string.userscript_code)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (code.isNotBlank()) onSaveCode(code) }) {
                    Text("OK")
                }
            }
        }
    }
}
