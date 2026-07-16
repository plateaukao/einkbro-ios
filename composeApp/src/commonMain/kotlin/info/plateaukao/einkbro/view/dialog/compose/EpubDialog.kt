package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import info.plateaukao.einkbro.preference.SavedFileInfo

/**
 * "Save as EPUB" dialog (parity Phase I). Mirrors Android's two-mode flow: save
 * the current page as a new book, or append it as a chapter to a previously
 * saved EinkBro EPUB. Shows a progress bar while the export runs.
 */
@Composable
fun EpubDialog(
    defaultTitle: String,
    savedEpubs: List<SavedFileInfo>,
    progress: Int?,
    onSaveNew: (bookName: String, chapterTitle: String) -> Unit,
    onAppend: (chapterTitle: String, path: String) -> Unit,
    onRemove: (SavedFileInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookName by remember { mutableStateOf(defaultTitle) }
    var chapterTitle by remember { mutableStateOf(defaultTitle) }

    Dialog(onDismissRequest = { if (progress == null) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Save as EPUB",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (progress != null) {
                Text("Exporting… $progress%", color = MaterialTheme.colors.onSurface)
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                return@Column
            }

            TextField(
                value = bookName,
                onValueChange = { bookName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Book name") },
            )
            Spacer(Modifier.width(8.dp))
            TextField(
                value = chapterTitle,
                onValueChange = { chapterTitle = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text("Chapter title") },
            )

            OutlinedButton(
                onClick = { onSaveNew(bookName, chapterTitle) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Save as new EPUB") }

            if (savedEpubs.isNotEmpty()) {
                Text(
                    "Add chapter to:",
                    color = MaterialTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Column(modifier = Modifier.heightIn(max = 220.dp)) {
                    savedEpubs.forEach { info ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                info.title,
                                color = MaterialTheme.colors.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAppend(chapterTitle, info.uri) }
                                    .padding(vertical = 14.dp),
                            )
                            IconButton(onClick = { onRemove(info) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colors.onSurface,
                                )
                            }
                        }
                        Divider()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
