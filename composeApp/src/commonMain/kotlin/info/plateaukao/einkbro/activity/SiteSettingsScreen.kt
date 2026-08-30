package info.plateaukao.einkbro.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.site_settings
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.dialog.compose.DEFAULT_DESKTOP_VIEWPORT_WIDTH
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsContent
import info.plateaukao.einkbro.view.dialog.compose.TextEditorDialogContent
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen host for the per-site settings on phones, where the centered
 * dialog is too cramped; iPad keeps using the dialog. Was SiteSettingsActivity;
 * finish() is replaced by [onClose].
 */
@Composable
fun SiteSettingsScreen(
    url: String,
    onClose: () -> Unit,
) {
    val config = AppServices.config
    var editorRequest by remember { mutableStateOf<EditorRequest?>(null) }

    Box(Modifier.fillMaxSize()) {
        ListScaffold(
            title = stringResource(Res.string.site_settings),
            onBack = onClose,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                SiteSettingsContent(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxHeight(),
                    url = url,
                    domainConfigs = config.domain,
                    globalFontSize = config.display.fontSize,
                    globalFontType = config.display.fontType,
                    globalBoldFont = config.display.boldFontStyle,
                    globalBlackFont = config.display.blackFontStyle,
                    globalFontBoldness = config.display.fontBoldness,
                    globalDesktopMode = config.browser.desktop,
                    defaultViewportWidth = DEFAULT_DESKTOP_VIEWPORT_WIDTH,
                    globalJavascript = config.browser.enableJavascript,
                    globalAdBlock = config.browser.adBlock,
                    globalCookies = config.browser.cookies,
                    globalTranslationMode = config.translation.translationMode,
                    onEditText = { title, initial, onResult ->
                        editorRequest = EditorRequest(title, initial, onResult)
                    },
                    onSave = { updatedConfig ->
                        config.updateDomainConfig(updatedConfig)
                        onClose()
                    },
                    onDeleteRule = { key ->
                        config.deleteSiteRule(key)
                        onClose()
                    },
                    onDismiss = onClose,
                )
            }
        }

        // Android shows TextEditorDialogFragment; here the editor overlays the screen.
        editorRequest?.let { request ->
            MyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        TextEditorDialogContent(
                            title = request.title,
                            initialText = request.initial,
                            onSave = { request.onResult(it) },
                            onDismiss = { editorRequest = null },
                        )
                    }
                }
            }
        }
    }
}

private data class EditorRequest(
    val title: String,
    val initial: String,
    val onResult: (String) -> Unit,
)
