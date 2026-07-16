package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Entry composable; was TranslationConfigDlgFragment.Content().
 */
@Composable
fun TranslationConfigDialogContent(
    url: String = "https://example.com/article",
    translateDirectly: Boolean = false,
    onToggledAction: (Boolean) -> Unit = {},
    onShowSiteSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val translationMode = remember { mutableStateOf(config.getTranslationMode(url)) }
    TranslationConfigScreen(
        translationMode = translationMode.value,
        translationModeChanged = {
            val host = Uri.parse(url).host
            if (host != null) {
                val domainConfig = config.getDomainConfig(url)
                domainConfig.translationMode = it
                config.updateDomainConfig(domainConfig)
            }
            translationMode.value = it
            if (translateDirectly || config.shouldTranslateSite(url)) {
                onToggledAction(true)
                onDismiss()
            }
        },
        onSiteSettingsClicked = if (onShowSiteSettings != null) {
            {
                onDismiss()
                onShowSiteSettings.invoke()
            }
        } else null,
    )
}

@Composable
fun TranslationConfigScreen(
    translationMode: TranslationMode = TranslationMode.GOOGLE_IN_PLACE,
    translationModeChanged: (TranslationMode) -> Unit = {},
    onSiteSettingsClicked: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var actionExpanded by remember { mutableStateOf(false) }

    val textLabel = "Mode: ${context.getString(translationMode.labelResId)}"
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        TextButton(onClick = { actionExpanded = true }) {
            Text(
                modifier = Modifier.padding(10.dp),
                text = textLabel,
                color = MaterialTheme.colors.onBackground
            )
        }
        DropdownMenu(
            modifier = Modifier.padding(8.dp),
            expanded = actionExpanded,
            onDismissRequest = { actionExpanded = false }
        ) {
            TranslationMode.entries.forEach { type ->
                val text = context.getString(type.labelResId)
                DropdownMenuItem(onClick = {
                    translationModeChanged(type)
                    actionExpanded = false
                }) {
                    Text(text = text)
                }
            }
        }
        if (onSiteSettingsClicked != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSiteSettingsClicked() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.site_settings),
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        }
    }
}
