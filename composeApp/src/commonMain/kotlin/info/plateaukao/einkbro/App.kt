package info.plateaukao.einkbro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.catalog.CatalogEntry
import info.plateaukao.einkbro.catalog.CatalogSection
import info.plateaukao.einkbro.catalog.bookmarksSection
import info.plateaukao.einkbro.catalog.coreDialogsSection
import info.plateaukao.einkbro.catalog.fontReaderSection
import info.plateaukao.einkbro.catalog.foundationSection
import info.plateaukao.einkbro.catalog.managementSection
import info.plateaukao.einkbro.catalog.settingsSection
import info.plateaukao.einkbro.catalog.translateTtsSection
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.dialog.DialogManager
import kotlinx.coroutines.delay

/** Sections shown on the catalog home; bundles append theirs here. */
private val sections: List<CatalogSection> = listOf(
    foundationSection,
    settingsSection,
    coreDialogsSection,
    fontReaderSection,
    translateTtsSection,
    bookmarksSection,
    managementSection,
)

@Composable
fun App() {
    // touch AppServices so config/koin initialize before any screen needs them
    remember { AppServices.config }

    var showCatalog by remember { mutableStateOf(false) }
    val browserViewModel = remember { info.plateaukao.einkbro.viewmodel.BrowserViewModel() }
    var current by remember { mutableStateOf<CatalogEntry?>(null) }

    MyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            if (!showCatalog) {
                Box(Modifier.fillMaxSize()) {
                    info.plateaukao.einkbro.view.compose.BrowserScreen(
                        browserViewModel = browserViewModel,
                        onOpenCatalog = { showCatalog = true },
                    )
                    ToastOverlay(Modifier.align(Alignment.BottomCenter))
                    OkCancelDialogHost()
                    SelectOptionDialogHost()
                    TextInputDialogHost()
                }
                return@Surface
            }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val entry = current
                if (entry == null) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCatalog = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to browser",
                                tint = MaterialTheme.colors.onBackground,
                            )
                            Text(
                                text = "Back to browser",
                                modifier = Modifier.padding(start = 12.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onBackground,
                            )
                        }
                        CatalogHome(onOpen = { current = it })
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { current = null }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colors.onBackground,
                            )
                            Text(
                                text = entry.name,
                                modifier = Modifier.padding(start = 12.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onBackground,
                            )
                        }
                        Box(Modifier.fillMaxSize()) {
                            entry.content { current = null }
                        }
                    }
                }

                ToastOverlay(Modifier.align(Alignment.BottomCenter))
                OkCancelDialogHost()
                SelectOptionDialogHost()
                TextInputDialogHost()
            }
        }
    }
}

@Composable
private fun CatalogHome(onOpen: (CatalogEntry) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "EinkBro UI Catalog",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                )
                Text(
                    "Compose Multiplatform port — tap a screen to verify it",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        sections.forEach { section ->
            item {
                Text(
                    section.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                )
            }
            items(section.entries) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(entry.name, fontSize = 17.sp, color = MaterialTheme.colors.onBackground)
                    if (entry.description.isNotEmpty()) {
                        Text(
                            entry.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToastOverlay(modifier: Modifier = Modifier) {
    val message by EBToast.current
    if (message != null) {
        LaunchedEffect(message) {
            delay(2200)
            EBToast.current.value = null
        }
        Box(
            modifier = modifier
                .padding(24.dp)
                .alpha(0.92f)
                .background(MaterialTheme.colors.onBackground),
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colors.background,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun OkCancelDialogHost() {
    val request by DialogManager.pendingOkCancel
    val req = request ?: return
    AlertDialog(
        onDismissRequest = { DialogManager.pendingOkCancel.value = null },
        title = req.title?.let { { Text(it) } },
        text = req.message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = {
                DialogManager.pendingOkCancel.value = null
                req.okAction()
            }) { Text("OK", color = MaterialTheme.colors.onBackground) }
        },
        dismissButton = {
            TextButton(onClick = {
                DialogManager.pendingOkCancel.value = null
                req.cancelAction?.invoke()
            }) { Text("Cancel", color = MaterialTheme.colors.onBackground) }
        },
        backgroundColor = MaterialTheme.colors.background,
    )
}

/** Renders enum/option pickers (Settings ListSettingItem etc.), Phase N. */
@Composable
private fun SelectOptionDialogHost() {
    val request by DialogManager.pendingSelectOption
    val req = request ?: return
    AlertDialog(
        onDismissRequest = { req.onResult(null) },
        title = { Text(req.title, color = MaterialTheme.colors.onBackground) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                req.options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { req.onResult(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == req.selectedIndex,
                            onClick = { req.onResult(index) },
                        )
                        Text(
                            text = option,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colors.onBackground,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { req.onResult(null) }) {
                Text("Cancel", color = MaterialTheme.colors.onBackground)
            }
        },
        backgroundColor = MaterialTheme.colors.background,
    )
}

/** Renders text-input settings (ValueSettingItem etc.), Phase N. */
@Composable
private fun TextInputDialogHost() {
    val request by DialogManager.pendingTextInput
    val req = request ?: return
    var text by remember(req) { mutableStateOf(req.initialValue) }
    AlertDialog(
        onDismissRequest = { req.onResult(null) },
        title = { Text(req.title, color = MaterialTheme.colors.onBackground) },
        text = {
            Column {
                req.description?.let {
                    Text(it, color = MaterialTheme.colors.onBackground)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { req.onResult(text) }) {
                Text("OK", color = MaterialTheme.colors.onBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = { req.onResult(null) }) {
                Text("Cancel", color = MaterialTheme.colors.onBackground)
            }
        },
        backgroundColor = MaterialTheme.colors.background,
    )
}
