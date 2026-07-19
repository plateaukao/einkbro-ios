package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import info.plateaukao.einkbro.preference.FabPosition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.preference.TouchAreaType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Touch-area page-turn zones (parity Phase F). Two tappable zones dispatch the
 * user's bound click/long-press gestures. Placement mirrors Android's
 * TouchAreaViewController/MainContentLayout: touchAreaType picks one of the
 * fixed-size zone layouts (150dp-wide boxes hugging the screen edges), and the
 * dashed hint border is shown persistently when touchAreaHint is on, or
 * flashed for one second when it is off — the zones themselves keep working
 * invisibly, exactly like Android. Ebook mode has no overlay zones (Android
 * drives that variant with JS inside the WebView).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.TouchAreaZones(onGesture: (BrowserAction) -> Unit) {
    val touch = AppServices.config.touch
    val type = touch.touchAreaType
    if (type == TouchAreaType.Ebook || type == TouchAreaType.LongLeftRight) return

    val allowLong = !touch.disableLongPressTouchArea
    val switch = touch.switchTouchAreaAction

    // Android TouchAreaViewController: click plays the bound up/down gesture
    // (switchTouchAreaAction swaps zones); long-press either plays the bound
    // long gesture or sends an arrow key when longClickAsArrowKey is set.
    val upZoneClick = { onGesture(if (!switch) touch.upClickGesture else touch.downClickGesture) }
    val downZoneClick = { onGesture(if (!switch) touch.downClickGesture else touch.upClickGesture) }
    val upZoneLong: (() -> Unit)? = if (!allowLong) null else {
        {
            if (touch.longClickAsArrowKey) onGesture(BrowserAction.SendLeftKey)
            else onGesture(if (!switch) touch.upLongClickGesture else touch.downLongClickGesture)
        }
    }
    val downZoneLong: (() -> Unit)? = if (!allowLong) null else {
        {
            if (touch.longClickAsArrowKey) onGesture(BrowserAction.SendRightKey)
            else onGesture(if (!switch) touch.downLongClickGesture else touch.upLongClickGesture)
        }
    }

    // Android shows the dashed border on enable/type change, then hides it
    // after one second unless the touchAreaHint pref keeps it on permanently.
    val hintPref = touch.touchAreaHint
    var hintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(type, hintPref) {
        hintVisible = true
        if (!hintPref) {
            delay(1000)
            hintVisible = false
        }
    }

    // Zone geometry from Android MainContentLayout (dp sizes, edge-anchored).
    val (upModifier, downModifier) = when (type) {
        TouchAreaType.MiddleLeftRight ->
            Modifier.align(Alignment.CenterStart).size(150.dp, 250.dp) to
                Modifier.align(Alignment.CenterEnd).size(150.dp, 250.dp)

        TouchAreaType.Left ->
            Modifier.align(Alignment.BottomStart).offset(y = (-150).dp).size(150.dp, 150.dp) to
                Modifier.align(Alignment.BottomStart).size(150.dp, 150.dp)

        TouchAreaType.Right ->
            Modifier.align(Alignment.BottomEnd).offset(y = (-150).dp).size(150.dp, 150.dp) to
                Modifier.align(Alignment.BottomEnd).size(150.dp, 150.dp)

        TouchAreaType.Long ->
            Modifier.align(Alignment.CenterStart).width(150.dp).fillMaxHeight() to
                Modifier.align(Alignment.CenterEnd).width(150.dp).fillMaxHeight()

        else -> // BottomLeftRight (default)
            Modifier.align(Alignment.BottomStart).size(150.dp, 250.dp) to
                Modifier.align(Alignment.BottomEnd).size(150.dp, 250.dp)
    }

    ZoneBox(upModifier, hintVisible, upZoneClick, upZoneLong)
    ZoneBox(downModifier, hintVisible, downZoneClick, downZoneLong)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoneBox(
    modifier: Modifier,
    hintVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Box(
        modifier
            .touchAreaHintBorder(hintVisible)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

/**
 * Android's touch_area_border drawable: a dashed black outline with an inset
 * dashed white outline, so the hint reads on both light and dark pages.
 */
private fun Modifier.touchAreaHintBorder(visible: Boolean): Modifier = drawBehind {
    if (!visible) return@drawBehind
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
    drawRect(
        color = Color.Black.copy(alpha = 0.75f),
        style = Stroke(width = 2f, pathEffect = dash),
    )
    val inset = 2.dp.toPx()
    drawRect(
        color = Color.White.copy(alpha = 0.8f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = 2f, pathEffect = dash),
    )
}

/**
 * Draggable floating action button (parity Phase F, enableNavButtonGesture).
 * A directional swipe on it dispatches navGesture{Up,Down,Left,Right}; a
 * long-press dispatches navButtonLongClickGesture. The button is repositionable
 * by dragging slowly (the swipe is only recognized as a gesture on quick flicks
 * — here any drag past the threshold both moves it and fires, matching the
 * e-ink "nudge to act" feel).
 */
@Composable
fun BoxScope.NavGestureFab(onGesture: (BrowserAction) -> Unit) {
    val touch = AppServices.config.touch
    // Android FabImageViewController honors fabPosition; NotShow suppresses the
    // button even with the gesture pref on.
    val fabPosition = AppServices.config.ui.fabPosition
    if (fabPosition == FabPosition.NotShow) return
    val anchor = when (fabPosition) {
        FabPosition.Left -> Alignment.BottomStart
        FabPosition.Center -> Alignment.BottomCenter
        else -> Alignment.BottomEnd
    }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    Surface(
        modifier = Modifier
            .align(anchor)
            .padding(24.dp)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(52.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    val action = touch.navButtonLongClickGesture
                    if (action != BrowserAction.Noop) onGesture(action)
                })
            }
            .pointerInput(Unit) {
                var startX = 0f
                var startY = 0f
                detectDragGestures(
                    onDragStart = { startX = 0f; startY = 0f },
                    onDrag = { change, drag ->
                        change.consume()
                        startX += drag.x
                        startY += drag.y
                        offsetX += drag.x
                        offsetY += drag.y
                    },
                    onDragEnd = {
                        val threshold = 60f
                        val action = when {
                            abs(startX) < threshold && abs(startY) < threshold -> null
                            abs(startX) > abs(startY) ->
                                if (startX > 0) touch.navGestureRight else touch.navGestureLeft
                            else -> if (startY > 0) touch.navGestureDown else touch.navGestureUp
                        }
                        if (action != null && action != BrowserAction.Noop) onGesture(action)
                        // Snap back to the anchor after a gesture flick.
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            },
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.OpenWith,
                contentDescription = "Navigation gestures",
                tint = MaterialTheme.colors.background,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
