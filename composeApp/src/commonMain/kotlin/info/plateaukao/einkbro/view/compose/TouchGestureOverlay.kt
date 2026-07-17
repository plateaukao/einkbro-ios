package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import info.plateaukao.einkbro.preference.FabPosition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.BrowserAction
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Touch-area page-turn zones (parity Phase F). Two tappable zones dispatch the
 * user's bound click/long-press gestures. Layout follows useUpDownPageTurn
 * (top/bottom vs left/right) and switchTouchAreaAction (which zone is "up").
 * touchAreaHint outlines the zones; disableLongPressTouchArea drops long-press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.TouchAreaZones(onGesture: (BrowserAction) -> Unit) {
    val touch = AppServices.config.touch
    val upClick = touch.upClickGesture
    val downClick = touch.downClickGesture
    val upLong = touch.upLongClickGesture
    val downLong = touch.downLongClickGesture
    val allowLong = !touch.disableLongPressTouchArea
    val hint = touch.touchAreaHint

    // switchTouchAreaAction swaps which physical zone plays the "up" gesture.
    val (firstClick, firstLong, secondClick, secondLong) = if (touch.switchTouchAreaAction) {
        Quad(downClick, downLong, upClick, upLong)
    } else {
        Quad(upClick, upLong, downClick, downLong)
    }

    if (touch.useUpDownPageTurn) {
        ZoneBox(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.35f),
            "▲", hint, allowLong, firstClick, firstLong, onGesture,
        )
        ZoneBox(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.35f),
            "▼", hint, allowLong, secondClick, secondLong, onGesture,
        )
    } else {
        ZoneBox(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.18f).fillMaxHeight(0.7f),
            "◀", hint, allowLong, firstClick, firstLong, onGesture,
        )
        ZoneBox(
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.18f).fillMaxHeight(0.7f),
            "▶", hint, allowLong, secondClick, secondLong, onGesture,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoneBox(
    modifier: Modifier,
    glyph: String,
    hint: Boolean,
    allowLong: Boolean,
    click: BrowserAction,
    long: BrowserAction,
    onGesture: (BrowserAction) -> Unit,
) {
    val hintColor = MaterialTheme.colors.onBackground.copy(alpha = 0.18f)
    Box(
        modifier
            .then(if (hint) Modifier.border(1.dp, hintColor) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onGesture(click) },
                onLongClick = if (allowLong) ({ onGesture(long) }) else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (hint) {
            Text(glyph, color = hintColor, style = MaterialTheme.typography.h5)
        }
    }
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

/** Small 4-tuple to destructure the zone binding selection. */
private data class Quad(
    val a: BrowserAction,
    val b: BrowserAction,
    val c: BrowserAction,
    val d: BrowserAction,
)

private operator fun Quad.component1() = a
private operator fun Quad.component2() = b
private operator fun Quad.component3() = c
private operator fun Quad.component4() = d
