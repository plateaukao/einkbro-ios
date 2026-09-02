package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.CustomFontInfo
import info.plateaukao.einkbro.preference.FontType
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.add_font_file
import info.plateaukao.einkbro.resources.custom_font
import info.plateaukao.einkbro.resources.font_preview_text
import info.plateaukao.einkbro.resources.no_fonts_added
import info.plateaukao.einkbro.resources.no_fonts_found
import info.plateaukao.einkbro.resources.search_fonts
import info.plateaukao.einkbro.util.FilePicker
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.PickKind
import info.plateaukao.einkbro.util.loadFontFamily
import info.plateaukao.einkbro.util.storedPathFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** A font file in the app's font store; [path] is absolute for this install. */
data class FontItem(
    val name: String,
    val path: String,
)

/** Documents sub-directory holding user-imported font files. */
const val FONT_STORE_DIR = "fonts"

private val fontExtensions = setOf("ttf", "otf", "ttc", "woff", "woff2")

/** Font files currently in the store, sorted by name. */
fun listStoredFonts(): List<FontItem> {
    val dir = FileStore.dirPath(FONT_STORE_DIR) ?: return emptyList()
    return FileStore.listFiles(FONT_STORE_DIR)
        .filter { it.substringAfterLast('.', "").lowercase() in fontExtensions }
        .map { FontItem(name = it, path = "$dir/$it") }
}

/**
 * Entry composable for the custom-font browser; was FontBrowserDialogFragment.Content().
 * Android lists font files from a user-picked SAF folder; iOS has no
 * persistent folder grant, so fonts are imported through the system document
 * picker into Documents/fonts and listed from there. Selecting one records a
 * Documents-relative path (see FileStore.storedPathFor — the container path
 * changes on reinstall) and switches the (reader) font type to CUSTOM.
 */
@Composable
fun FontBrowserDialogContent(
    isReaderMode: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    var fonts by remember { mutableStateOf(listStoredFonts()) }
    FontBrowserScreen(
        fonts = fonts,
        onAddFont = {
            FilePicker.pick(PickKind.Font) { name, bytes ->
                if (bytes.isEmpty()) return@pick
                FileStore.writeBytes(FONT_STORE_DIR, name, bytes)
                fonts = listStoredFonts()
            }
        },
        onDeleteFont = { item ->
            FileStore.delete(item.path)
            fonts = listStoredFonts()
        },
        onFontSelected = { item ->
            val info = CustomFontInfo(item.name, storedPathFor(item.path))
            if (isReaderMode) {
                config.display.readerCustomFontInfo = info
                config.display.readerFontType = FontType.CUSTOM
            } else {
                config.display.customFontInfo = info
                config.display.fontType = FontType.CUSTOM
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun FontBrowserScreen(
    fonts: List<FontItem>,
    onAddFont: () -> Unit,
    onDeleteFont: (FontItem) -> Unit,
    onFontSelected: (FontItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredFonts = remember(fonts, searchQuery) {
        if (searchQuery.isBlank()) fonts
        else fonts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .width(320.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.clickable { onDismiss() },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(Res.string.custom_font),
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddFont) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(Res.string.add_font_file),
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }

        if (fonts.isNotEmpty()) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(Res.string.search_fonts),
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    cursorColor = MaterialTheme.colors.onBackground,
                    focusedIndicatorColor = MaterialTheme.colors.onBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )

            HorizontalSeparator()

            if (filteredFonts.isEmpty()) {
                Text(
                    stringResource(Res.string.no_fonts_found),
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(filteredFonts, key = { it.path }) { fontItem ->
                        FontItemRow(
                            fontItem = fontItem,
                            onClick = { onFontSelected(fontItem) },
                            onDelete = { onDeleteFont(fontItem) },
                        )
                    }
                }
            }
        } else {
            // Nothing imported yet
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.no_fonts_added),
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }

        DialogOkButtonBar(okAction = onDismiss)
    }
}

@Composable
private fun FontItemRow(
    fontItem: FontItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // The real typeface, parsed off the main thread (CJK fonts run to tens of
    // MB); the preview line appears once it's ready, as on Android.
    val fontFamily by produceState<FontFamily?>(initialValue = null, fontItem.path) {
        value = withContext(Dispatchers.Default) {
            FileStore.readBytes(fontItem.path)?.let { loadFontFamily(fontItem.path, it) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = fontItem.name,
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fontFamily != null) {
                Text(
                    text = stringResource(Res.string.font_preview_text),
                    color = MaterialTheme.colors.onBackground,
                    fontFamily = fontFamily,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // iOS-only: with no folder to manage in the Files app, the list is the
        // only place a mistaken import can be removed.
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
    HorizontalSeparator()
}
