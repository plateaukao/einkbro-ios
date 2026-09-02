package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.FontType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.custom_scale
import info.plateaukao.einkbro.resources.custom_scale_desc

/**
 * Entry composable for the reader-mode font dialog; was
 * ReaderFontDialogFragment.Content(). Reuses MainFontDialog with the
 * reader-specific config fields.
 */
@Composable
fun ReaderFontDialogContent(
    onFontCustomizeClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val customFontName = remember {
        mutableStateOf(config.display.readerCustomFontInfo?.name.orEmpty())
    }
    val scope = rememberCoroutineScope()
    MainFontDialog(
        selectedFontSizeValue = config.display.readerFontSize,
        customFontSizeValue = config.display.customFontSize,
        selectedFontType = config.display.readerFontType,
        customFontName = customFontName.value,
        onFontSizeClick = {
            config.display.readerFontSize = it
            onDismiss()
        },
        onFontTypeClick = {
            if (it == FontType.CUSTOM && config.display.readerCustomFontInfo == null) {
                onFontCustomizeClick()
            } else {
                config.display.readerFontType = it
                onDismiss()
            }
        },
        onFontTypeChanged = onFontCustomizeClick,
        onCustomFontSizeClick = {
            // Android: TextInputDialog(custom_scale, custom_scale_desc); null = cancelled.
            scope.launch {
                val value = AppServices.dialogManager.getTextInput(
                    Res.string.custom_scale,
                    Res.string.custom_scale_desc,
                    config.display.customFontSize.toString(),
                )?.trim()?.toIntOrNull() ?: return@launch
                config.display.readerFontSize = value
                config.display.customFontSize = value
                onDismiss()
            }
        },
        okAction = { onDismiss() },
    )
}
