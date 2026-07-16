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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
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

    var current by remember { mutableStateOf<CatalogEntry?>(null) }

    MyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val entry = current
                if (entry == null) {
                    CatalogHome(onOpen = { current = it })
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
