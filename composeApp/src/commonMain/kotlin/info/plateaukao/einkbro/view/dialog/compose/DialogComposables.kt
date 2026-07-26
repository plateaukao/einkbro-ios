package info.plateaukao.einkbro.view.dialog.compose

import android.graphics.Point
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ToolbarPosition
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

fun Offset.toScreenPoint(boxPosition: Offset): Point {
    return Point((x + boxPosition.x).toInt(), (y + boxPosition.y).toInt())
}

/**
 * On Android this is the DialogFragment base class; its companion carries the
 * anchor of the last-tapped toolbar icon so dialogs can open next to it. The
 * ported dialogs are plain composables, so only the anchor state remains;
 * [AnchoredDialogFrame] is the consumer.
 */
object ComposeDialogFragment {
    var anchorX: Int = -1
    var anchorY: Int = -1
}

/**
 * Positions dialog content the way Android's ComposeDialogFragment window does:
 * snapped to the toolbar's edge (config.ui.toolbarPosition) and centered on the
 * toolbar icon that opened it ([ComposeDialogFragment.anchorX]/[anchorY], written
 * by Toolbar.kt on every icon tap/long-press), clamped to the window. With no
 * anchor it falls back to the trailing corner of that edge, matching Android's
 * Gravity.BOTTOM|END default. Host it in a Dialog with
 * DialogProperties(usePlatformDefaultWidth = false) so the frame spans the window.
 */
@Composable
fun AnchoredDialogFrame(
    onDismiss: (() -> Unit)? = null,
    // Pass false when the content scrolls on its own (LazyColumn/grid inside);
    // nesting it in verticalScroll would measure it with infinite height and crash.
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Snapshot and consume the anchor so a stale value can't leak into the next
    // dialog (Android does the same in onCreateDialog).
    val anchor = remember {
        val a = ComposeDialogFragment.anchorX to ComposeDialogFragment.anchorY
        ComposeDialogFragment.anchorX = -1
        ComposeDialogFragment.anchorY = -1
        a
    }
    val toolbarPosition = AppServices.config.ui.toolbarPosition
    val margin = with(LocalDensity.current) { 10.dp.roundToPx() }

    // Tap outside the card dismisses. Pointer-only on purpose (not clickable):
    // key events bubbling from focused children must not count as clicks.
    val dismissModifier = if (onDismiss != null) {
        Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
    } else Modifier

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(dismissModifier),
        content = {
            Surface(
                modifier = Modifier
                    .wrapContentSize()
                    .border(1.dp, MaterialTheme.colors.onBackground, RoundedCornerShape(5.dp)),
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colors.background,
            ) {
                if (scrollable) {
                    Box(Modifier.verticalScroll(rememberScrollState())) {
                        content()
                    }
                } else {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val card = measurables.first().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (maxW - 2 * margin).coerceAtLeast(0),
                maxHeight = (maxH - 2 * margin).coerceAtLeast(0),
            )
        )
        layout(maxW, maxH) {
            // The anchor is in window pixels; map it into this layout's space
            // (offset by the safe-area padding above).
            val local = coordinates?.windowToLocal(
                Offset(anchor.first.toFloat(), anchor.second.toFloat())
            ) ?: Offset(anchor.first.toFloat(), anchor.second.toFloat())
            val xMax = (maxW - card.width - margin).coerceAtLeast(margin)
            val yMax = (maxH - card.height - margin).coerceAtLeast(margin)
            val x: Int
            val y: Int
            when (toolbarPosition) {
                ToolbarPosition.Left, ToolbarPosition.Right -> {
                    x = if (toolbarPosition == ToolbarPosition.Left) margin else xMax
                    y = if (anchor.second >= 0) {
                        (local.y - card.height / 2f).roundToInt().coerceIn(margin, yMax)
                    } else margin
                }

                else -> {
                    y = if (toolbarPosition == ToolbarPosition.Top) margin else yMax
                    x = if (anchor.first >= 0) {
                        (local.x - card.width / 2f).roundToInt().coerceIn(margin, xMax)
                    } else xMax
                }
            }
            card.place(x, y)
        }
    }
}

/**
 * Positions dialog content at a long-press point, the way Android's context-menu
 * fragments do: ContextMenuDialogFragment/BookmarkContextMenuDlgFragment set
 * Gravity.TOP|START and the window x/y to the touch point, so the card's top-left
 * lands there — offset far enough below to leave the pressed line of text visible.
 * [point] is in window pixels. Near an edge the card flips to the point's other
 * side per axis rather than sliding over the finger (Android just lets its window
 * clamp); an invalid point (Android's `Point.isValid()`, x or
 * y == 0) falls back to centered. Card chrome matches [AnchoredDialogFrame]. Host
 * it in a Dialog with DialogProperties(usePlatformDefaultWidth = false) so the
 * frame spans the window.
 */
@Composable
fun PointAnchoredDialogFrame(
    point: Point?,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val margin = with(LocalDensity.current) { 10.dp.roundToPx() }
    // Vertical clearance from the touch point: the reported point is inside the
    // pressed line of text, so the card is pushed a line-height clear of it (up
    // or down) instead of landing on top of the link that was long-pressed.
    val touchGap = with(LocalDensity.current) { 20.dp.roundToPx() }

    // Tap outside the card dismisses; pointer-only, as in AnchoredDialogFrame.
    val dismissModifier = if (onDismiss != null) {
        Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
    } else Modifier

    Layout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .then(dismissModifier),
        content = {
            Surface(
                modifier = Modifier
                    .wrapContentSize()
                    .border(1.dp, MaterialTheme.colors.onBackground, RoundedCornerShape(5.dp)),
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colors.background,
            ) {
                content()
            }
        },
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val card = measurables.first().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (maxW - 2 * margin).coerceAtLeast(0),
                maxHeight = (maxH - 2 * margin).coerceAtLeast(0),
            )
        )
        layout(maxW, maxH) {
            if (point == null || point.x == 0 || point.y == 0) {
                card.place((maxW - card.width) / 2, (maxH - card.height) / 2)
                return@layout
            }
            // Window pixels -> this layout's space (offset by the safe-area
            // padding above). Callers may pass screen coordinates instead
            // (positionOnScreen); on a fullscreen iOS app the two coincide.
            val local = coordinates?.windowToLocal(
                Offset(point.x.toFloat(), point.y.toFloat())
            ) ?: Offset(point.x.toFloat(), point.y.toFloat())
            val xMax = (maxW - card.width - margin).coerceAtLeast(margin)
            val yMax = (maxH - card.height - margin).coerceAtLeast(margin)

            // Per axis: keep the card past the touch point when it fits there,
            // else flip it to the point's other side so the finger isn't covered
            // (a link near the bottom/right edge gets the menu above/left of it).
            // Only a card too big for either side falls back to edge-clamping.
            // [gap] is the clearance held on both sides of the point — vertical
            // only, since the pressed text runs horizontally through it.
            fun anchor(touch: Int, size: Int, maxStart: Int, gap: Int): Int = when {
                touch + gap <= maxStart -> (touch + gap).coerceAtLeast(margin)
                touch - gap - size >= margin -> touch - gap - size
                else -> maxStart
            }
            card.place(
                anchor(local.x.roundToInt(), card.width, xMax, gap = 0),
                anchor(local.y.roundToInt(), card.height, yMax, gap = touchGap),
            )
        }
    }
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

@Composable
fun DialogButtonBar(
    okResId: StringResource? = null,
    dismissAction: () -> Unit,
    okAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            modifier = Modifier.wrapContentWidth(),
            onClick = dismissAction
        ) {
            Text(
                "Cancel",
                color = MaterialTheme.colors.onBackground
            )
        }
        VerticalSeparator()
        TextButton(
            modifier = Modifier.wrapContentWidth(),
            onClick = { dismissAction(); okAction() }) {
            Text(
                okResId?.let { stringResource(it) } ?: "OK",
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}
