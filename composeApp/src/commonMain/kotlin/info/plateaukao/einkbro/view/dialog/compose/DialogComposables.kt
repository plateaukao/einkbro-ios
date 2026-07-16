package info.plateaukao.einkbro.view.dialog.compose

import android.graphics.Point
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

fun Offset.toScreenPoint(boxPosition: Offset): Point {
    return Point((x + boxPosition.x).toInt(), (y + boxPosition.y).toInt())
}

/**
 * On Android this is the DialogFragment base class; its companion carries the
 * anchor of the last-tapped toolbar icon so dialogs can open next to it. The
 * ported dialogs are plain composables, so only the anchor state remains.
 */
object ComposeDialogFragment {
    var anchorX: Int = -1
    var anchorY: Int = -1
}

@Composable
fun HorizontalSeparator() {
    Divider(thickness = 1.dp, color = MaterialTheme.colors.onBackground)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIcon(
    modifier: Modifier,
    iconResId: DrawableResource,
    action: (() -> Unit)? = null,
    longClickAction: (() -> Unit)? = null,
) {
    Icon(
        modifier = modifier
            .size(36.dp)
            .padding(end = 5.dp)
            .combinedClickable(
                onClick = { action?.invoke() },
                onLongClick = { longClickAction?.invoke() },
            ),
        imageVector = vectorResource(iconResId),
        contentDescription = null,
        tint = MaterialTheme.colors.onBackground
    )
}

@Composable
fun VerticalSeparator() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(color = MaterialTheme.colors.onBackground)
    )
}
