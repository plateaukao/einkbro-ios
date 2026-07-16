package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.view.EBToast
import org.jetbrains.compose.resources.stringResource

data class FontItem(
    val name: String,
    val uri: Uri,
)

/**
 * Entry composable for the custom-font browser; was FontBrowserDialogFragment.Content().
 * The Android original lists font files from a user-picked SAF folder; here a
 * representative sample list stands in and the folder picker is a toast.
 */
@Composable
fun FontBrowserDialogContent(
    isReaderMode: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val context = LocalContext.current
    var folderUri by remember {
        mutableStateOf(config.display.fontFolderUri ?: "content://sample/fonts")
    }
    FontBrowserScreen(
        folderUri = folderUri,
        onSelectFolder = {
            EBToast.show(context, "would open the system folder picker")
            folderUri = "content://sample/fonts"
        },
        onFontSelected = { fontInfo ->
            if (isReaderMode) {
                config.display.readerCustomFontInfo = fontInfo
                config.display.readerFontType = FontType.CUSTOM
            } else {
                config.display.customFontInfo = fontInfo
                config.display.fontType = FontType.CUSTOM
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

private val sampleFontItems = listOf(
    "NotoSerifCJKtc-Regular.otf",
    "NotoSansCJKtc-Regular.otf",
    "SourceHanSerif-Bold.otf",
    "Iansui-Regular.ttf",
    "KleeOne-Regular.ttf",
).map { FontItem(name = it, uri = Uri.parse("content://sample/fonts/$it")) }

private fun listFontsFromFolder(folderUri: String): List<FontItem> =
    if (folderUri.isBlank()) emptyList() else sampleFontItems

@Composable
fun FontBrowserScreen(
    folderUri: String?,
    onSelectFolder: () -> Unit,
    onFontSelected: (CustomFontInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var fontItems by remember { mutableStateOf<List<FontItem>>(emptyList()) }

    LaunchedEffect(folderUri) {
        fontItems = if (folderUri.isNullOrBlank()) {
            emptyList()
        } else {
            listFontsFromFolder(folderUri)
        }
    }

    val filteredFonts = remember(fontItems, searchQuery) {
        if (searchQuery.isBlank()) fontItems
        else fontItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
            TextButton(onClick = onSelectFolder) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (folderUri.isNullOrBlank()) stringResource(Res.string.select_font_folder)
                    else stringResource(Res.string.change_folder),
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }

        if (!folderUri.isNullOrBlank()) {
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
                    items(filteredFonts, key = { it.uri.toString() }) { fontItem ->
                        FontItemRow(
                            fontItem = fontItem,
                            onClick = {
                                onFontSelected(
                                    CustomFontInfo(fontItem.name, fontItem.uri.toString())
                                )
                            },
                        )
                    }
                }
            }
        } else {
            // No folder selected prompt
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.select_font_folder),
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
) {
    // Android builds a Typeface from the font file; the catalog shows a
    // representative platform serif so the preview row still renders.
    val fontFamily = remember(fontItem.uri) { FontFamily.Serif }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = (-1).dp,
                color = MaterialTheme.colors.onBackground,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = fontItem.name,
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.font_preview_text),
            color = MaterialTheme.colors.onBackground,
            fontFamily = fontFamily,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        HorizontalSeparator()
    }
}
