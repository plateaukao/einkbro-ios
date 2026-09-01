package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.preference.DarkMode
import info.plateaukao.einkbro.preference.DisplayConfig
import info.plateaukao.einkbro.preference.GRADIENT_END_FRACTION
import info.plateaukao.einkbro.preference.GRADIENT_START_FRACTION
import info.plateaukao.einkbro.preference.ThemePalette
import info.plateaukao.einkbro.preference.UiBorder
import info.plateaukao.einkbro.preference.UiFill
import info.plateaukao.einkbro.preference.UiTheme
import info.plateaukao.einkbro.preference.isPattern
import info.plateaukao.einkbro.preference.palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Holds the currently selected [UiTheme] as Compose state so every MyTheme
 * root recomposes immediately when the user picks a new theme in Settings.
 * Seeded from config by [syncFrom] (AppServices init, backup restore); kept
 * in sync by the DisplayConfig setters.
 */
object UiThemeState {
    val current: MutableState<UiTheme> = mutableStateOf(UiTheme.CLASSIC)
    val darkMode: MutableState<DarkMode> = mutableStateOf(DarkMode.SYSTEM)
    val customColor: MutableState<Color> = mutableStateOf(Color(0xFF4A90D9))
    val uiBorder: MutableState<UiBorder> = mutableStateOf(UiBorder.CLASSIC)
    val uiFill: MutableState<UiFill> = mutableStateOf(UiFill.NONE)
    val inverted: MutableState<Boolean> = mutableStateOf(false)
    val gradientAngle: MutableState<Int> = mutableStateOf(45)
    val gradientLevel: MutableState<Int> = mutableStateOf(100)

    /** Android EinkBroApplication.onCreate + the prefs-change listener, in one. */
    fun syncFrom(display: DisplayConfig) {
        current.value = display.uiTheme
        darkMode.value = display.darkMode
        customColor.value = Color(display.customThemeColor)
        uiBorder.value = display.uiBorder
        uiFill.value = display.uiFill
        inverted.value = display.uiThemeInverted
        gradientAngle.value = display.gradientAngle
        gradientLevel.value = display.gradientLevel
    }
}

private fun Float.toRadians(): Float = (this * PI / 180.0).toFloat()

/**
 * Linear gradient at an arbitrary angle (degrees; 0 = left-to-right,
 * 90 = top-down), spanning the drawn box regardless of its size.
 */
class AngleGradientBrush(
    private val gradientColors: List<Color>,
    private val angleDegrees: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = angleDegrees.toRadians()
        val dx = cos(rad)
        val dy = sin(rad)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val halfLen = (abs(dx) * size.width + abs(dy) * size.height) / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * halfLen, cy - dy * halfLen),
            to = Offset(cx + dx * halfLen, cy + dy * halfLen),
            colors = gradientColors,
        )
    }
}

/**
 * Postage-stamp outline: straight edges with evenly spaced semicircular
 * perforation bites. Corners stay square (bites keep clear of them), so the
 * corner region remains plain vertical/horizontal edge.
 */
fun stampShape(scallopRadius: Dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { scallopRadius.toPx() }
        val path = Path()
        val w = size.width
        val h = size.height

        fun biteCenters(edge: Float): List<Float> {
            // keep a long straight run at each corner (about 3 bite radii)
            val margin = 3f * r
            val span = edge - 2f * margin
            if (span < 2f * r) return listOf(edge / 2f)
            val n = max(1, (span / (3.5f * r)).toInt())
            val step = span / n
            return List(n) { margin + (it + 0.5f) * step }
        }

        fun arc(cx: Float, cy: Float, startDeg: Float) {
            path.arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startDeg, -180f, forceMoveTo = false)
        }

        path.moveTo(0f, 0f)
        biteCenters(w).forEach { cx -> path.lineTo(cx - r, 0f); arc(cx, 0f, 180f) }
        path.lineTo(w, 0f)
        biteCenters(h).forEach { cy -> path.lineTo(w, cy - r); arc(w, cy, 270f) }
        path.lineTo(w, h)
        biteCenters(w).map { w - it }.forEach { cx -> path.lineTo(cx + r, h); arc(cx, h, 0f) }
        path.lineTo(0f, h)
        biteCenters(h).map { h - it }.forEach { cy -> path.lineTo(0f, cy + r); arc(0f, cy, 90f) }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Hand-drawn outline: the rectangle's perimeter walked in short segments,
 * each vertex nudged by a deterministic pseudo-random offset (stable for a
 * given size, so recompositions don't make the line shimmer).
 */
fun sketchShape(amplitude: Dp): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = with(density) { amplitude.toPx() }
        val step = with(density) { 14.dp.toPx() }
        // inset the rectangle by the wobble amplitude so the jittered line
        // never leaves the bounds (it would be cropped by clipping or covered
        // by neighboring items)
        val inset = a + 1f
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        val path = Path()
        fun jitter(i: Int): Float {
            val h = sin(i * 12.9898 + size.width + size.height) * 43758.5453
            return ((h - floor(h)).toFloat() * 2f - 1f) * a
        }
        var idx = 0
        fun edge(x1: Float, y1: Float, x2: Float, y2: Float, first: Boolean) {
            val len = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
            val n = max(2, (len / step).toInt())
            // perpendicular unit vector for the nudge
            val px = -(y2 - y1) / len
            val py = (x2 - x1) / len
            for (k in 0..n) {
                val t = k.toFloat() / n
                val j = if (k == 0 || k == n) 0f else jitter(idx)
                idx++
                val x = x1 + (x2 - x1) * t + px * j
                val y = y1 + (y2 - y1) * t + py * j
                if (first && k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        edge(left, top, right, top, first = true)
        edge(right, top, right, bottom, first = false)
        edge(right, bottom, left, bottom, first = false)
        edge(left, bottom, left, top, first = false)
        path.close()
        return Outline.Generic(path)
    }
}

/** Applies the user's gradient level (percent) to a spec blend fraction. */
fun Float.withGradientLevel(levelPercent: Int): Float =
    (this * levelPercent / 100f).coerceIn(0f, 0.9f)

/**
 * Whether the app UI should use the dark palette: the Dark mode setting wins
 * (Force on / Disabled), and Follow system falls back to the system setting.
 * This gives every UiTheme a dark variant independent of system dark mode.
 */
@Composable
fun isAppInDarkTheme(): Boolean = UiThemeState.inverted.value || when (UiThemeState.darkMode.value) {
    DarkMode.FORCE_ON -> true
    DarkMode.DISABLED -> false
    DarkMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Content color for the app's TopAppBars. M2's TopAppBar background is
 * primarySurface: the accent (primary) in light mode but surface (black) in
 * dark mode — so bar content must be onPrimary in light and onSurface in
 * dark. (Dark onPrimary is black for accent-filled buttons and would be
 * invisible on the dark bar.)
 */
val Colors.onTopBar: Color get() = if (isLight) onPrimary else onSurface

/** Accent-tinted fill color for the TONAL fill. */
@Composable
fun tonalFillColor(): Color = lerp(
    MaterialTheme.colors.background,
    MaterialTheme.colors.primary,
    if (MaterialTheme.colors.isLight) 0.10f else 0.16f,
)

/** Gradient brush for the GRADIENT fill at the user's angle and level. */
@Composable
fun gradientFillBrush(): Brush {
    val angle = UiThemeState.gradientAngle.value
    val level = UiThemeState.gradientLevel.value
    return AngleGradientBrush(
        listOf(
            lerp(
                MaterialTheme.colors.background,
                MaterialTheme.colors.primary,
                GRADIENT_START_FRACTION.withGradientLevel(level),
            ),
            lerp(
                MaterialTheme.colors.background,
                MaterialTheme.colors.primary,
                GRADIENT_END_FRACTION.withGradientLevel(level),
            ),
        ),
        angle.toFloat(),
    )
}

/** Draws a repeating pattern fill (stripes, dots, graph, ruled, crosshatch). */
fun DrawScope.drawFillPattern(fill: UiFill, lineColor: Color) {
    when (fill) {
        UiFill.STRIPES -> {
            val p = 14.dp.toPx()
            val w = 1.5.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(lineColor, Offset(x, size.height), Offset(x + size.height, 0f), w)
                x += p
            }
        }
        UiFill.DOTS -> {
            val p = 14.dp.toPx()
            val r = 1.5.dp.toPx()
            var y = p / 2f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) p / 2f else p
                while (x < size.width) {
                    drawCircle(lineColor, r, Offset(x, y))
                    x += p
                }
                y += p
                row++
            }
        }
        UiFill.GRAPH -> {
            val p = 16.dp.toPx()
            val w = 0.8.dp.toPx()
            var x = p
            while (x < size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), w); x += p
            }
            var y = p
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), w); y += p
            }
        }
        UiFill.RULED -> {
            val p = 18.dp.toPx()
            val w = 1.dp.toPx()
            var y = p
            while (y < size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), w); y += p
            }
        }
        UiFill.CROSSHATCH -> {
            val p = 16.dp.toPx()
            val w = 0.8.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(lineColor, Offset(x, size.height), Offset(x + size.height, 0f), w)
                drawLine(lineColor, Offset(x, 0f), Offset(x + size.height, size.height), w)
                x += p
            }
        }
        else -> Unit
    }
}

/** Line color used by the pattern fills — kept faint so text stays readable. */
@Composable
fun patternLineColor(): Color = lerp(
    MaterialTheme.colors.background,
    MaterialTheme.colors.primary,
    if (MaterialTheme.colors.isLight) 0.12f else 0.16f,
)

/** Pattern fill clipped to a shape, as a modifier. */
fun Modifier.patternFill(fill: UiFill, shape: Shape, lineColor: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    clipPath(path) { drawFillPattern(fill, lineColor) }
}

/** The outline shape the current border draws for an item or a dialog frame. */
@Composable
fun themedFrameShape(frame: Boolean = false): Shape {
    val border = UiThemeState.uiBorder.value
    return when (border) {
        UiBorder.STAMP -> stampShape(4.dp)
        UiBorder.SKETCH -> sketchShape(2.5.dp)
        else -> RoundedCornerShape((if (frame) border.frameRadiusDp else border.itemRadiusDp).dp)
    }
}

/**
 * Extra content inset a border style needs beyond the plain classic 1dp
 * look — the iOS counterpart of Android ThemedBorders.contentPad. Irregular
 * outlines reach into the bounds (stamp bites, sketch wobble, the
 * certificate's inner line, paper's inner frame, the sticker's shadow
 * offset), so the framed box grows by this much instead of letting the
 * border crowd or cross the content. Item frames apply it even when
 * currently unframed, so selecting an item never changes its layout size.
 */
fun UiBorder.contentPad(frame: Boolean): Dp {
    val styleExtra = when (this) {
        UiBorder.NONE, UiBorder.CLASSIC, UiBorder.ROUND -> 0f
        UiBorder.SHARP -> 1f
        UiBorder.DASHED -> 1f
        // outer line + 2dp gap + inner line already inset the content via
        // the border chain's own padding; keep a hairline of clearance
        UiBorder.PAPER -> 1f
        UiBorder.STAMP -> 4f      // scallop bite radius
        UiBorder.SKETCH -> 4f     // wobble amplitude + safety inset
        UiBorder.CERTIFICATE -> 8f // outer 3 + gap 4 + inner line
        UiBorder.STICKER -> 3f    // shadow offset shrinks the front box
    }
    // dialog windows additionally grow by the stroke and a breathing ring
    return if (frame) (styleExtra + widthDp + 2f).dp else styleExtra.dp
}

/**
 * Themed frame for in-content bordered items, combining the independent
 * border and fill preferences. A non-positive widthOverride keeps the call
 * site's "no frame in this state" behavior; a positive one keeps its
 * emphasis (never thinner than the border's width).
 */
fun Modifier.ebItemFrame(
    widthOverride: Dp? = null,
    // paints an opaque theme background clipped to the frame shape first —
    // for floating panels that must cover the content behind them without
    // spilling a rectangular fill past an irregular (stamp/sketch) outline
    paintBackground: Boolean = false,
    // dialog windows / floating panels use the border's frame radius
    frame: Boolean = false,
): Modifier = composed {
    val border = UiThemeState.uiBorder.value
    val fill = UiThemeState.uiFill.value
    // the style's content inset applies framed or not, so an item keeps the
    // same layout size whether or not it is currently selected/bordered
    val contentPad = border.contentPad(frame)
    if (widthOverride != null && widthOverride <= 0.dp) return@composed padding(contentPad)
    // the fill must clip to the border's actual outline (stamp bites,
    // sketch wobble), not to a plain rounded rect
    val shape = themedFrameShape(frame)
    val radius = if (frame) border.frameRadiusDp else border.itemRadiusDp
    val width = maxOf(widthOverride ?: 0.dp, border.widthDp.dp)
    val accent = MaterialTheme.colors.primary
    val bg = MaterialTheme.colors.background
    val withBase = if (paintBackground) background(bg, shape) else this

    if (border == UiBorder.STICKER) {
        // draw shadow, fill, and outline entirely inside the bounds (the
        // front box is inset by the shadow offset), so nothing is cropped
        // by or overlaps neighboring items
        val fillBrush: Brush? = when (fill) {
            UiFill.TONAL -> SolidColor(tonalFillColor())
            UiFill.GRADIENT -> gradientFillBrush()
            else -> null
        }
        val patternColor = patternLineColor()
        return@composed drawBehind {
            val off = 3.dp.toPx()
            val corner = CornerRadius(radius.dp.toPx())
            val boxSize = Size(size.width - off, size.height - off)
            drawRoundRect(accent, topLeft = Offset(off, off), size = boxSize, cornerRadius = corner)
            drawRoundRect(bg, size = boxSize, cornerRadius = corner)
            if (fillBrush != null) {
                drawRoundRect(fillBrush, size = boxSize, cornerRadius = corner)
            } else if (fill.isPattern()) {
                val clip = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, boxSize.width, boxSize.height, corner))
                }
                clipPath(clip) { drawFillPattern(fill, patternColor) }
            }
            drawRoundRect(accent, size = boxSize, cornerRadius = corner, style = Stroke(width.toPx()))
        }.padding(contentPad)
    }

    val m: Modifier = when (fill) {
        UiFill.NONE -> withBase
        UiFill.TONAL -> withBase.background(tonalFillColor(), shape)
        UiFill.GRADIENT -> withBase.background(gradientFillBrush(), shape)
        else -> withBase.patternFill(fill, shape, patternLineColor())
    }

    val framed = when (border) {
        UiBorder.NONE, UiBorder.STICKER -> m
        UiBorder.CLASSIC, UiBorder.ROUND, UiBorder.SHARP ->
            m.border(width, accent, shape)
        // print-like double frame: two thin concentric lines (this is what
        // the picker preview shows; it used to render as a single line)
        UiBorder.PAPER -> m
            .border(width, accent, shape)
            .padding(width + 2.dp)
            .border(width, accent, RoundedCornerShape((radius - 3f).coerceAtLeast(0f).dp))
        UiBorder.DASHED -> m.dashedBorder(width, radius.dp, accent)
        UiBorder.STAMP, UiBorder.SKETCH -> m.border(width, accent, shape)
        UiBorder.CERTIFICATE -> m.drawBehind {
            val outer = 3.dp.toPx()
            drawRect(
                color = accent,
                topLeft = Offset(outer / 2f, outer / 2f),
                size = Size(size.width - outer, size.height - outer),
                style = Stroke(outer),
            )
            val inset = outer + 4.dp.toPx()
            val innerW = size.width - 2 * inset
            val innerH = size.height - 2 * inset
            // the hairline inner frame only fits once the box is big
            // enough; on tiny chips a degenerate rect would scribble
            if (innerW > 4.dp.toPx() && innerH > 4.dp.toPx()) {
                drawRect(
                    color = accent,
                    topLeft = Offset(inset, inset),
                    size = Size(innerW, innerH),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
    framed.padding(contentPad)
}

/**
 * Themed window frame for dialogs and floating panels — the runtime stand-in
 * for Android's ThemedBorders.dialogFrame drawable: opaque theme background,
 * the selected fill, and the selected border at its frame radius.
 */
fun Modifier.ebDialogFrame(): Modifier = ebItemFrame(paintBackground = true, frame = true)

/**
 * Divider that follows the selected UiBorder, so separators speak the same
 * visual language as the frames around them: dashed borders get dashed
 * lines, the stamp's perforated edge gets a dotted line, sketch gets a
 * wobbly hand-drawn line, paper/certificate get double rules. Drop-in
 * replacement for material Divider (defaults to the accent color, matching
 * the app's existing primary-colored separators).
 */
/**
 * Layout band [ThemedDivider] occupies for the current border style — public
 * so containers with otherwise fixed heights (the toolbar) can grow by
 * exactly the divider's band instead of squeezing their content.
 */
@Composable
fun themedDividerHeight(thickness: Dp = 1.dp): Dp =
    when (UiThemeState.uiBorder.value) {
        UiBorder.PAPER, UiBorder.CERTIFICATE, UiBorder.SKETCH -> 5.dp
        UiBorder.STAMP -> 3.dp
        UiBorder.SHARP, UiBorder.STICKER -> maxOf(thickness, 2.dp)
        else -> thickness
    }

@Composable
fun ThemedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.primary,
    thickness: Dp = 1.dp,
) {
    val border = UiThemeState.uiBorder.value
    val height = themedDividerHeight(thickness)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind { drawThemedDividerLine(border, color, thickness.toPx()) }
    )
}

/** Band height of [ThemedEdgeBorder]. */
val EDGE_BORDER_BAND = 5.dp

/**
 * The toolbar's page-facing edge as a TRUE themed border: the accent edge
 * line in the current border style, theme background filled on the toolbar
 * side of the line, and full transparency on the page side — the same
 * inside-opaque / outside-transparent semantics as the dialog frames (stamp
 * bites and sketch wobble are die-cut, showing the page through them).
 * [edgeAtTop] is true when the toolbar sits below the band (bottom toolbar).
 */
@Composable
fun ThemedEdgeBorder(
    edgeAtTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val border = UiThemeState.uiBorder.value
    val accent = MaterialTheme.colors.primary
    val bg = MaterialTheme.colors.background
    Box(
        modifier
            .fillMaxWidth()
            .height(EDGE_BORDER_BAND)
            .drawBehind { drawThemedEdgeBorder(border, accent, bg, edgeAtTop) }
    )
}

private fun DrawScope.drawThemedEdgeBorder(
    border: UiBorder,
    accent: Color,
    bg: Color,
    edgeAtTop: Boolean,
) {
    val w = size.width
    val h = size.height
    // Edge space: v=0 is the page-facing side of the band, growing toward the
    // toolbar. Mirrored vertically when the toolbar is above the band.
    fun y(v: Float): Float = if (edgeAtTop) v else h - v
    fun fillFrom(v: Float) {
        if (edgeAtTop) drawRect(bg, Offset(0f, v), Size(w, h - v))
        else drawRect(bg, Offset(0f, 0f), Size(w, h - v))
    }
    fun hline(v: Float, stroke: Float, effect: PathEffect? = null) {
        drawLine(accent, Offset(0f, y(v)), Offset(w, y(v)), stroke, pathEffect = effect)
    }
    when (border) {
        UiBorder.STAMP -> {
            // straight edge with perforation bites cut into the surface,
            // matching stampShape's spacing; the page shows through the bites
            val r = 3.dp.toPx()
            val edgeV = 0.75.dp.toPx()
            val margin = 3f * r
            val span = w - 2f * margin
            val centers = if (span < 2f * r) listOf(w / 2f) else {
                val n = max(1, (span / (3.5f * r)).toInt())
                val step = span / n
                List(n) { margin + (it + 0.5f) * step }
            }
            val edge = Path()
            edge.moveTo(0f, y(edgeV))
            centers.forEach { cx ->
                edge.lineTo(cx - r, y(edgeV))
                val rect = Rect(cx - r, y(edgeV) - r, cx + r, y(edgeV) + r)
                edge.arcTo(rect, 180f, if (edgeAtTop) -180f else 180f, false)
            }
            edge.lineTo(w, y(edgeV))
            val fill = Path().apply {
                addPath(edge)
                lineTo(w, y(h))
                lineTo(0f, y(h))
                close()
            }
            drawPath(fill, bg)
            drawPath(edge, accent, style = Stroke(1.25.dp.toPx()))
        }
        UiBorder.SKETCH -> {
            // wobbly hand-drawn edge; background follows the wobble
            val a = 1.5.dp.toPx()
            val step = 14.dp.toPx()
            val base = a + 0.75.dp.toPx()
            val n = max(2, (w / step).toInt())
            val edge = Path()
            for (k in 0..n) {
                val x = w * k / n
                val hsh = sin(k * 12.9898 + w) * 43758.5453
                val j = if (k == 0 || k == n) 0f
                    else ((hsh - floor(hsh)).toFloat() * 2f - 1f) * a
                if (k == 0) edge.moveTo(x, y(base + j)) else edge.lineTo(x, y(base + j))
            }
            val fill = Path().apply {
                addPath(edge)
                lineTo(w, y(h))
                lineTo(0f, y(h))
                close()
            }
            drawPath(fill, bg)
            drawPath(edge, accent, style = Stroke(1.5.dp.toPx()))
        }
        UiBorder.PAPER -> {
            fillFrom(0.5.dp.toPx())
            hline(0.5.dp.toPx(), 1.dp.toPx())
            hline(3.5.dp.toPx(), 1.dp.toPx())
        }
        UiBorder.CERTIFICATE -> {
            fillFrom(1.25.dp.toPx())
            hline(1.25.dp.toPx(), 2.5.dp.toPx())
            hline(4.25.dp.toPx(), 1.dp.toPx())
        }
        UiBorder.DASHED -> {
            fillFrom(0.75.dp.toPx())
            hline(
                0.75.dp.toPx(), 1.5.dp.toPx(),
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f),
            )
        }
        // NONE, CLASSIC, ROUND, SHARP, STICKER: solid edge at the border's weight
        else -> {
            val stroke = max(border.widthDp, 1f).dp.toPx()
            fillFrom(stroke / 2f)
            hline(stroke / 2f, stroke)
        }
    }
}

/**
 * Page-load progress line in the theme's border language: the themed divider
 * pattern (dots for stamp, wobble for sketch, double rule for paper...)
 * revealed left-to-right by the load fraction.
 */
@Composable
fun ThemedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    // same accent as the themed borders/dividers
    color: Color = MaterialTheme.colors.primary,
) {
    val border = UiThemeState.uiBorder.value
    Box(
        modifier
            .fillMaxWidth()
            .height(themedDividerHeight(2.dp))
            .drawBehind {
                clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
                    drawThemedDividerLine(border, color, 2.dp.toPx())
                }
            }
    )
}

private fun DrawScope.drawThemedDividerLine(border: UiBorder, color: Color, baseWidth: Float) {
    val midY = size.height / 2f
    val w = size.width
    when (border) {
        UiBorder.SHARP ->
            drawLine(color, Offset(0f, midY), Offset(w, midY), maxOf(baseWidth, 2.dp.toPx()))
        UiBorder.DASHED ->
            drawLine(
                color, Offset(0f, midY), Offset(w, midY), maxOf(baseWidth, 1.5.dp.toPx()),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f,
                ),
            )
        UiBorder.STAMP -> {
            // perforation: a run of small dots, centered horizontally
            val pitch = 6.dp.toPx()
            val r = 1.2.dp.toPx()
            val n = max(1, (w / pitch).toInt())
            var x = (w - (n - 1) * pitch) / 2f
            repeat(n) {
                drawCircle(color, r, Offset(x, midY))
                x += pitch
            }
        }
        UiBorder.SKETCH -> {
            // wobbly hand-drawn line, deterministic per width so it doesn't
            // shimmer across recompositions
            val a = 1.5.dp.toPx()
            val step = 14.dp.toPx()
            val n = max(2, (w / step).toInt())
            val path = Path()
            for (k in 0..n) {
                val x = w * k / n
                val h = sin(k * 12.9898 + w) * 43758.5453
                val j = if (k == 0 || k == n) 0f else ((h - floor(h)).toFloat() * 2f - 1f) * a
                if (k == 0) path.moveTo(x, midY + j) else path.lineTo(x, midY + j)
            }
            drawPath(path, color, style = Stroke(1.2.dp.toPx()))
        }
        UiBorder.PAPER -> {
            // double thin rule
            drawLine(color, Offset(0f, midY - 1.5.dp.toPx()), Offset(w, midY - 1.5.dp.toPx()), 1.dp.toPx())
            drawLine(color, Offset(0f, midY + 1.5.dp.toPx()), Offset(w, midY + 1.5.dp.toPx()), 1.dp.toPx())
        }
        UiBorder.CERTIFICATE -> {
            // thick rule with a hairline echo, like the frame
            drawLine(color, Offset(0f, midY - 1.5.dp.toPx()), Offset(w, midY - 1.5.dp.toPx()), 2.dp.toPx())
            drawLine(color, Offset(0f, midY + 1.5.dp.toPx()), Offset(w, midY + 1.5.dp.toPx()), 0.8.dp.toPx())
        }
        UiBorder.STICKER -> {
            val sw = maxOf(baseWidth, 2.dp.toPx())
            drawLine(
                color, Offset(sw / 2f, midY), Offset(w - sw / 2f, midY), sw,
                cap = StrokeCap.Round,
            )
        }
        UiBorder.NONE, UiBorder.CLASSIC, UiBorder.ROUND ->
            drawLine(color, Offset(0f, midY), Offset(w, midY), baseWidth)
    }
}

@Composable
fun MyTheme(
    darkTheme: Boolean = isAppInDarkTheme(),
    content: @Composable () -> Unit
) {
    val uiTheme by UiThemeState.current
    val customColor by UiThemeState.customColor
    val inverted by UiThemeState.inverted
    MaterialTheme(
        colors = remember(uiTheme, customColor, darkTheme, inverted) {
            val palette = uiTheme.palette(customColor)
            when {
                // inverted: the theme's dark text shade becomes the background
                // and the light background tint becomes the text color
                inverted -> palette.toInvertedColors()
                darkTheme -> palette.toDarkColors()
                else -> palette.toLightColors()
            }
        },
        content = content,
    )
}

val NormalTextModifier = Modifier.padding(6.dp)

// The accent lands on primary/secondary(+variants): Material components
// (Button, Switch, Checkbox, TextField cursor, ProgressIndicator) pick it up
// automatically; borders, dividers, and toolbar icons reference
// MaterialTheme.colors.primary. Text and screen surfaces come from the
// theme's onBackground/background so a theme can tint them too. CLASSIC's
// values reproduce the original hardcoded black-and-white palette exactly.
private fun ThemePalette.toLightColors() = lightColors(
    primary = accent,
    primaryVariant = accent,
    onPrimary = Color.White,
    secondary = accent,
    secondaryVariant = accent,
    onSecondary = Color.White,
    surface = background,
    onSurface = onBackground,
    background = background,
    onBackground = onBackground,
)

private fun ThemePalette.toInvertedColors() = darkColors(
    primary = accentDark,
    primaryVariant = accentDark,
    onPrimary = Color.Black,
    secondary = accentDark,
    onSecondary = Color.Black,
    surface = onBackground,
    onSurface = background,
    background = onBackground,
    onBackground = background,
)

private fun ThemePalette.toDarkColors() = darkColors(
    primary = accentDark,
    primaryVariant = accentDark,
    onPrimary = Color.Black,
    secondary = accentDark,
    onSecondary = Color.Black,
    surface = Color.Black,
    onSurface = onBackgroundDark,
    background = Color.Black,
    onBackground = onBackgroundDark,
)
