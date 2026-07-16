package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.FontType
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.compose.SelectableText
import org.jetbrains.compose.resources.stringResource

/**
 * Entry composable for the (web page) font dialog; was FontDialogFragment.Content().
 */
@Composable
fun FontDialogContent(
    onFontTypeChanged: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val context = LocalContext.current
    val customFontName = remember {
        mutableStateOf(config.display.customFontInfo?.name.orEmpty())
    }
    val fontSizeState = remember { mutableIntStateOf(config.display.fontSize) }
    MainFontDialog(
        selectedFontSizeValue = fontSizeState.value,
        customFontSizeValue = config.display.customFontSize,
        selectedFontType = config.display.fontType,
        customFontName = customFontName.value,
        onFontSizeClick = {
            config.display.fontSize = it
            fontSizeState.value = it
            onDismiss()
        },
        onFontTypeClick = {
            if (it == FontType.CUSTOM && config.display.customFontInfo == null) {
                onFontTypeChanged()
            } else {
                config.display.fontType = it
                onDismiss()
            }
        },
        onFontTypeChanged = { onFontTypeChanged() },
        onCustomFontSizeClick = {
            // Android shows a TextInputDialog for a custom scale value.
            EBToast.show(context, "would ask for a custom font scale")
        },
        okAction = { onDismiss() },
    )
}

val fontSizeList1 = listOf(
    75,
    90,
    100,
    110,
)

val fontSizeList2 = listOf(
    125,
    150,
    175,
    200,
)

val fontSizeList3 = listOf(
    -1 // Custom
)

@Composable
fun MainFontDialog(
    selectedFontSizeValue: Int,
    customFontSizeValue: Int,
    selectedFontType: FontType,
    customFontName: String,
    onFontSizeClick: (Int) -> Unit,
    onFontTypeClick: (FontType) -> Unit,
    onFontTypeChanged: () -> Unit,
    onCustomFontSizeClick: () -> Unit,
    okAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            .width(IntrinsicSize.Max)
    ) {
        Text(
            stringResource(Res.string.font_size),
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
        )
        Row {
            fontSizeList1.map { fontSize ->
                val isSelect = selectedFontSizeValue == fontSize
                SelectableText(
                    modifier = Modifier
                        .padding(horizontal = 1.dp, vertical = 3.dp),
                    selected = isSelect,
                    text = "$fontSize%",
                ) {
                    onFontSizeClick(fontSize)
                }
            }
        }

        Row {
            fontSizeList2.map { fontSize ->
                val isSelect = selectedFontSizeValue == fontSize
                SelectableText(
                    modifier = Modifier
                        .padding(horizontal = 1.dp, vertical = 3.dp),
                    selected = isSelect,
                    text = "$fontSize%",
                ) {
                    onFontSizeClick(fontSize)
                }
            }
        }
        Row {
            fontSizeList3.map { fontSize ->
                val isSelect = selectedFontSizeValue !in fontSizeList1 && selectedFontSizeValue !in fontSizeList2
                val text = if (customFontSizeValue !in fontSizeList1 && customFontSizeValue !in fontSizeList2) {
                    "$customFontSizeValue%"
                } else {
                    stringResource(Res.string.custom_scale)
                }
                SelectableText(
                    modifier = Modifier
                        .padding(horizontal = 1.dp, vertical = 3.dp),
                    selected = isSelect,
                    text = text,
                ) {
                    onCustomFontSizeClick()
                }
            }
        }

        Text(
            stringResource(Res.string.font_type),
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
        )
        Column {
            FontType.values().map { fontType ->
                val isSelect = fontType == selectedFontType
                if (fontType != FontType.CUSTOM) {
                    SelectableText(
                        modifier = Modifier.padding(horizontal = 1.dp, vertical = 5.dp),
                        selected = isSelect,
                        text = stringResource(fontType.resId),
                    ) {
                        onFontTypeClick(fontType)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val fontName = customFontName.ifBlank {
                            stringResource(Res.string.nothing)
                        }
                        SelectableText(
                            modifier = Modifier
                                .padding(horizontal = 1.dp, vertical = 5.dp)
                                .weight(1f),
                            selected = isSelect,
                            text = stringResource(fontType.resId) + " ($fontName)",
                        ) {
                            onFontTypeClick(fontType)
                        }
                        IconButton(
                            onClick = onFontTypeChanged,
                        ) {
                            Icon(
                                tint = MaterialTheme.colors.onBackground,
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(Res.string.settings),
                            )
                        }
                    }
                }
            }
        }
        DialogOkButtonBar(
            okAction = okAction,
        )
    }
}

@Composable
fun DialogOkButtonBar(
    okAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
    ) {
        HorizontalSeparator()
        TextButton(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxWidth(),
            onClick = okAction
        ) {
            Text(
                // android.R.string.ok has no CMP counterpart in strings.xml
                "OK",
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}

@Composable
fun PreviewMainFontDialog() {
    MyTheme {
        MainFontDialog(
            selectedFontSizeValue = 100,
            customFontSizeValue = 120,
            selectedFontType = FontType.SYSTEM_DEFAULT,
            customFontName = "Noto Sans CJK TC",
            onFontSizeClick = {},
            onFontTypeClick = {},
            onFontTypeChanged = {},
            onCustomFontSizeClick = {},
            okAction = {},
        )
    }
}
