package info.plateaukao.einkbro.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import info.plateaukao.einkbro.view.compose.ebDialogFrame
import info.plateaukao.einkbro.view.compose.themedFrameShape

/**
 * Dialog wrapper used across the app in place of compose's Dialog: EinkBro
 * dialogs never dim the page behind them (Android's ComposeDialogFragment
 * sets window dimAmount to 0f), so the scrim is forced transparent whatever
 * the caller passes. Swap in via `import ...NoDimDialog as Dialog`.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoDimDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
            scrimColor = Color.Transparent,
        ),
        content = content,
    )
}

/**
 * m2 AlertDialog stand-in on top of [NoDimDialog] — CMP's non-Android
 * AlertDialog offers no way to clear its scrim. Same parameter shape as the
 * call sites use; swap in via `import ...NoDimAlertDialog as AlertDialog`.
 */
@Composable
fun NoDimAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
) {
    NoDimDialog(
        onDismissRequest = onDismissRequest,
        // dismissOnClickOutside stays off: with the software-keyboard inset
        // shifting the dialog, CMP's outside-click detection misfires on the
        // very tap that focuses a text field, dismissing the dialog instantly.
        // AlertDialogs always have explicit Cancel/OK buttons anyway.
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            // imePadding: with onFocusBehavior=DoNothing the scene is not
            // panned for the keyboard; padding the centered dialog re-centers
            // it in the space left above the ime.
            // ebDialogFrame draws the themed window chrome: opaque theme
            // background clipped to the border's actual outline, so irregular
            // frames (stamp bites, sketch wobble, sticker shadow) show the
            // content behind instead of a white rectangle.
            modifier = Modifier.imePadding().then(modifier).ebDialogFrame(),
            shape = themedFrameShape(frame = true),
            color = Color.Transparent,
            contentColor = contentColor,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
                title?.let {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                        ProvideTextStyle(MaterialTheme.typography.subtitle1, it)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                text?.let {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        ProvideTextStyle(MaterialTheme.typography.body2, it)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
