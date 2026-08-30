package info.plateaukao.einkbro.view.dialog.compose

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.SaveHistoryMode
import info.plateaukao.einkbro.preference.toggle
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.MyTheme
import org.jetbrains.compose.resources.StringResource

@Composable
fun FastToggleDialogContent(
    extraAction: () -> Unit = {},
    onOpenWhitelist: (info.plateaukao.einkbro.activity.WhiteListType) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    FastToggleItemList(
        LocalContext.current,
        AppServices.config,
        onOpenWhitelist = { type -> onDismiss(); onOpenWhitelist(type) },
    ) { needExtraAction ->
        if (needExtraAction) extraAction()
        onDismiss()
    }
}

@Composable
fun FastToggleItemList(
    context: Context,
    config: ConfigManager,
    onOpenWhitelist: (info.plateaukao.einkbro.activity.WhiteListType) -> Unit = {},
    onClicked: ((Boolean) -> Unit),
) {
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        ToggleItem(
            state = config.isIncognitoMode,
            titleResId = Res.string.setting_title_incognito,
            imageVector = vectorResource(Res.drawable.ic_incognito)
        ) {
            config::isIncognitoMode.toggle()
            onClicked(true)
        }
        ToggleItem(
            state = config.browser.adBlock,
            titleResId = Res.string.setting_title_adblock, imageVector = Icons.Outlined.Block,
            onEditAction = {
                onOpenWhitelist(info.plateaukao.einkbro.activity.WhiteListType.Adblock)
            }
        ) {
            config.browser::adBlock.toggle()
            onClicked(true)
        }
        ToggleItem(
            state = config.browser.enableJavascript,
            titleResId = Res.string.setting_title_javascript, imageVector = Icons.Outlined.Terminal,
            onEditAction = {
                onOpenWhitelist(info.plateaukao.einkbro.activity.WhiteListType.Javascript)
            }
        ) {
            config.browser::enableJavascript.toggle()
            onClicked(true)
        }
        ToggleItem(
            state = config.browser.cookies,
            titleResId = Res.string.setting_title_cookie, imageVector = Icons.Outlined.Cookie,
            onEditAction = {
                onOpenWhitelist(info.plateaukao.einkbro.activity.WhiteListType.Cookie)
            }
        ) {
            config.browser::cookies.toggle()
            onClicked(true)
        }
        ToggleItem(
            state = config.tab.isSaveHistoryOn(),
            titleResId = Res.string.history, imageVector = Icons.Outlined.AccessTime
        ) { on ->
            if (on) {
                config.tab.saveHistoryMode = config.tab.toggledSaveHistoryMode
            } else {
                config.tab.toggledSaveHistoryMode = config.tab.saveHistoryMode
                config.tab.saveHistoryMode = SaveHistoryMode.DISABLED
            }
            onClicked(false)
        }

        Divider(thickness = 1.dp, color = MaterialTheme.colors.primary)

        ToggleItem(
            state = config.browser.shareLocation,
            titleResId = Res.string.location, imageVector = Icons.Outlined.LocationOn
        ) {
            config.browser::shareLocation.toggle()
            onClicked(false)
        }
        ToggleItem(
            state = config.touch.volumePageTurn,
            titleResId = Res.string.volume_page_turn, imageVector = Icons.AutoMirrored.Outlined.VolumeUp
        ) {
            config.touch::volumePageTurn.toggle()
            onClicked(false)
        }
        ToggleItem(
            state = config.browser.continueMedia,
            titleResId = Res.string.media_continue, imageVector = Icons.Outlined.MusicNote
        ) {
            config.browser::continueMedia.toggle()
            onClicked(false)
        }
        ToggleItem(
            state = config.browser.desktop,
            titleResId = Res.string.desktop_mode, imageVector = Icons.Outlined.DesktopWindows
        ) {
            config.browser::desktop.toggle()
            onClicked(false)
        }
    }
}

@Composable
fun ToggleItem(
    state: Boolean,
    titleResId: StringResource,
    imageVector: ImageVector? = null,
    isEnabled: Boolean = true,
    onEditAction: (() -> Unit)? = null,
    onClicked: (Boolean) -> Unit,
) {
    var currentState by remember { mutableStateOf(state) }

    Row(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .height(46.dp)
            .padding(4.dp)
            .clickable {
                if (isEnabled) {
                    currentState = !currentState
                    onClicked(currentState)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = currentState,
            enabled = isEnabled,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.onBackground,
                uncheckedColor = MaterialTheme.colors.onBackground,
                checkmarkColor = MaterialTheme.colors.background,
            ),
            onCheckedChange = {
                if (isEnabled) {
                    currentState = !currentState
                    onClicked(currentState)
                }
            }
        )
        if (imageVector != null) {
            Icon(
                imageVector = imageVector, contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxHeight(),
                tint = MaterialTheme.colors.onBackground
            )
        }
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
        )
        Text(
            modifier = Modifier.wrapContentWidth(),
            text = stringResource(titleResId),
            fontSize = 18.sp,
            color = MaterialTheme.colors.onBackground
        )
        if (onEditAction != null) {
            Icon(
                imageVector = vectorResource(Res.drawable.icon_edit), contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxHeight()
                    .clickable { onEditAction() },
                tint = MaterialTheme.colors.onBackground
            )
        }
    }
}

@Composable
private fun PreviewItem() {
    MyTheme {
        ToggleItem(true, Res.string.title, Icons.Outlined.LocationOn, onEditAction = {}) {}
    }
}

@Composable
private fun PreviewItemList() {
    MyTheme {
        //FastToggleItemList(config = ConfigManager(), onClicked = {})
    }
}
