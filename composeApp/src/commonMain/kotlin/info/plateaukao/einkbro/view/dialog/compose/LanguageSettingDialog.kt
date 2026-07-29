package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.compose.SelectableText
import info.plateaukao.einkbro.view.dialog.TranslationLanguageDialog
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API
import info.plateaukao.einkbro.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Entry composable; was LanguageSettingDialogFragment.Content().
 * Only the target language is selectable: the source-language picker existed
 * for Papago, which is not ported.
 */
@Composable
fun LanguageSettingDialogContent(
    translateApi: TRANSLATE_API = TRANSLATE_API.GOOGLE,
    translationViewModel: TranslationViewModel = remember { TranslationViewModel() },
    translate: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    TranslationLanguageSetting(
        shouldShowSourceLanguage = false,
        translationViewModel = translationViewModel,
        changeTranslationLanguage = {
            scope.launch {
                val language = TranslationLanguageDialog(context).show() ?: return@launch
                config.translation.translationLanguage = language
                translationViewModel.updateTranslationLanguage(language)
            }
        },
        translate = translate,
        dismiss = onDismiss,
    )
}

@Composable
fun TranslationLanguageSetting(
    shouldShowSourceLanguage: Boolean,
    translationViewModel: TranslationViewModel,
    changeTranslationLanguage: () -> Unit,
    changeSourceLanguage: () -> Unit = {},
    translate: () -> Unit,
    dismiss: () -> Unit,
) {
    val targetLanguage by translationViewModel.translationLanguage.collectAsState()
    val sourceLanguage by translationViewModel.sourceLanguage.collectAsState()
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .wrapContentWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = stringResource(Res.string.translation_language),
            style = MaterialTheme.typography.h5.copy(color = MaterialTheme.colors.onBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shouldShowSourceLanguage) {
                SelectableText(
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                    selected = true,
                    text = sourceLanguage.language,
                    textAlign = TextAlign.Center,
                    onClick = changeSourceLanguage
                )
                Text(
                    text = "→",
                    color = MaterialTheme.colors.onBackground,
                )
            }
            SelectableText(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
                selected = true,
                text = targetLanguage.language,
                textAlign = TextAlign.Center,
                onClick = changeTranslationLanguage
            )
        }
        HorizontalSeparator()
        DialogButtonBar(
            okResId = Res.string.translate,
            dismissAction = { dismiss() },
            okAction = {
                translate()
                dismiss()
            }
        )
    }
}


