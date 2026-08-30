package info.plateaukao.einkbro.preference

import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

enum class PaperSize(val sizeString: String) {
    ISO_13("A4 (13\")"),
    SIZE_10("A5 (10\")"),
    ISO_67("Hisense A7 (6.7\")"),
    SIZE_8("C6 (8\")"),
}

enum class FabPosition {
    Right, Left, Center, NotShow, Custom
}

@kotlinx.serialization.Serializable
enum class TranslationMode(val labelResId: StringResource) {
    GOOGLE_URL(Res.string.google_full_page),
    GOOGLE_IN_PLACE(Res.string.google_in_place),
    TRANSLATE_BY_PARAGRAPH(Res.string.translate_by_paragraph),
    OPENAI_BY_PARAGRAPH(Res.string.openai_translate_by_paragraph),
    GEMINI_BY_PARAGRAPH(Res.string.gemini_translate_by_paragraph),
    OPENAI_IN_PLACE(Res.string.openai_in_place),
    GEMINI_IN_PLACE(Res.string.gemini_in_place),
}

@kotlinx.serialization.Serializable
enum class FontType(val resId: StringResource) {
    SYSTEM_DEFAULT(Res.string.system_default),
    SERIF(Res.string.serif),
    GOOGLE_SERIF(Res.string.googleserif),
    CUSTOM(Res.string.custom_font),
    TC_IANSUI(Res.string.iansui_tc),
    JA_MINCHO(Res.string.mincho_ja),
    KO_GAMJA(Res.string.gamja_flower_ko)
}

enum class DarkMode {
    SYSTEM, FORCE_ON, DISABLED
}

// Persisted by ordinal; only append new entries.
enum class NewTabBehavior {
    START_INPUT, SHOW_HOME, SHOW_RECENT_BOOKMARKS, SHOW_START_PAGE
}

enum class ShareLongPressAction(val labelResId: StringResource) {
    COPY_LINK(Res.string.share_long_press_copy_link),
    LAST_SHARE_TARGET(Res.string.share_long_press_last_target),
}

enum class HighlightStyle(
    val color: Color?,
    val stringResId: StringResource,
    val iconResId: DrawableResource,
) {
    UNDERLINE(
        null,
        Res.string.underline,
        Res.drawable.ic_underscore,
    ),
    BACKGROUND_YELLOW(
        Color.Yellow,
        Res.string.yellow,
        Res.drawable.ic_highlight_color,
    ),
    BACKGROUND_GREEN(
        Color.Green,
        Res.string.green,
        Res.drawable.ic_highlight_color,
    ),
    BACKGROUND_BLUE(
        Color.Blue,
        Res.string.blue,
        Res.drawable.ic_highlight_color,
    ),
    BACKGROUND_PINK(
        Color.Red,
        Res.string.pink,
        Res.drawable.ic_highlight_color,
    ),
}

enum class TranslationTextStyle(
    val stringResId: StringResource,
) {
    NONE(Res.string.none),
    DASHED_BORDER(Res.string.dashed_border),
    VERTICAL_LINE(Res.string.vertical_line),
    GRAY(Res.string.gray),
    BOLD(Res.string.bold),
}

enum class SaveHistoryMode {
    SAVE_WHEN_OPEN, SAVE_WHEN_CLOSE, DISABLED
}

enum class EinkImageAdjustment(val strength: Int, val labelResId: StringResource) {
    OFF(0, Res.string.eink_image_off),
    LEVEL_10(10, Res.string.eink_image_10),
    LEVEL_30(30, Res.string.eink_image_30),
    LEVEL_50(50, Res.string.eink_image_50),
    LEVEL_70(70, Res.string.eink_image_70),
    LEVEL_100(100, Res.string.eink_image_100),
}

// DEEP re-encodes images at the network layer (full pipeline incl. dithering);
// FAST injects a CSS filter instead: no CPU/re-encode cost, and it also covers
// data:/blob: URIs and JS-generated images, but can't dither.
enum class EinkImageMode(val labelResId: StringResource) {
    DEEP(Res.string.eink_image_mode_deep),
    FAST(Res.string.eink_image_mode_fast),
}

enum class ToolbarPosition {
    Bottom, Top, Left, Right
}

// ---------------------------------------------------------------------------
// UI theming (Android feature/ui-color-themes): color theme + border + fill.
// ---------------------------------------------------------------------------

// UI accent color themes. Persisted by ordinal; only append new entries.
// accent/accentDark: buttons, borders, dividers, toolbar icons, top bar.
// background/onBackground: light-mode surface tint and text color.
// onBackgroundDark: dark-mode text color (dark background stays black for e-ink).
enum class UiTheme(
    val accent: androidx.compose.ui.graphics.Color,
    val accentDark: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    val onBackground: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black,
    val onBackgroundDark: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Gray,
) {
    CLASSIC(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color(0xFFAAAAAA)),
    LIGHT_BLUE(
        androidx.compose.ui.graphics.Color(0xFF4A90D9), androidx.compose.ui.graphics.Color(0xFF8FBCE8),
        androidx.compose.ui.graphics.Color(0xFFF3F7FC), androidx.compose.ui.graphics.Color(0xFF1B3A5C),
        androidx.compose.ui.graphics.Color(0xFF9FB6CC),
    ),
    DARK_BLUE(
        androidx.compose.ui.graphics.Color(0xFF16437E), androidx.compose.ui.graphics.Color(0xFF7A9CC6),
        androidx.compose.ui.graphics.Color(0xFFF2F5FA), androidx.compose.ui.graphics.Color(0xFF122F58),
        androidx.compose.ui.graphics.Color(0xFF97A8C0),
    ),
    GREEN(
        androidx.compose.ui.graphics.Color(0xFF2E7D32), androidx.compose.ui.graphics.Color(0xFF81C784),
        androidx.compose.ui.graphics.Color(0xFFF2F8F2), androidx.compose.ui.graphics.Color(0xFF1B421D),
        androidx.compose.ui.graphics.Color(0xFF9DB89E),
    ),
    SEPIA(
        androidx.compose.ui.graphics.Color(0xFF795548), androidx.compose.ui.graphics.Color(0xFFBCAAA4),
        androidx.compose.ui.graphics.Color(0xFFF7F1E3), androidx.compose.ui.graphics.Color(0xFF3E2C23),
        androidx.compose.ui.graphics.Color(0xFFB3A79B),
    ),
    PURPLE(
        androidx.compose.ui.graphics.Color(0xFF673AB7), androidx.compose.ui.graphics.Color(0xFFB39DDB),
        androidx.compose.ui.graphics.Color(0xFFF6F3FB), androidx.compose.ui.graphics.Color(0xFF32205C),
        androidx.compose.ui.graphics.Color(0xFFA99BC4),
    ),
    RED(
        androidx.compose.ui.graphics.Color(0xFFC62828), androidx.compose.ui.graphics.Color(0xFFE57373),
        androidx.compose.ui.graphics.Color(0xFFFBF3F2), androidx.compose.ui.graphics.Color(0xFF571A17),
        androidx.compose.ui.graphics.Color(0xFFC09A98),
    ),

    // Colors ignored: the palette is derived from DisplayConfig.customThemeColor.
    CUSTOM(androidx.compose.ui.graphics.Color(0xFF4A90D9), androidx.compose.ui.graphics.Color(0xFF8FBCE8)),
}

// Resolved colors for the current theme; equals the enum's fixed colors for
// the preset themes and a derived palette for CUSTOM.
data class ThemePalette(
    val accent: androidx.compose.ui.graphics.Color,
    val accentDark: androidx.compose.ui.graphics.Color,
    val background: androidx.compose.ui.graphics.Color,
    val onBackground: androidx.compose.ui.graphics.Color,
    val onBackgroundDark: androidx.compose.ui.graphics.Color,
)

fun UiTheme.palette(customColor: androidx.compose.ui.graphics.Color): ThemePalette =
    if (this == UiTheme.CUSTOM) deriveThemePalette(customColor)
    else ThemePalette(accent, accentDark, background, onBackground, onBackgroundDark)

/** RGB -> HSV (hue 0..360, sat/value 0..1); stands in for android.graphics.Color.colorToHSV. */
fun androidx.compose.ui.graphics.Color.toHsv(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    return floatArrayOf(h, s, max)
}

/** HSV -> opaque color; stands in for android.graphics.Color.HSVToColor. */
fun hsvColor(hue: Float, sat: Float, value: Float): androidx.compose.ui.graphics.Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = sat.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return androidx.compose.ui.graphics.Color(r1 + m, g1 + m, b1 + m)
}

/**
 * Derives a readable palette from an arbitrary base color: the accent is
 * clamped so it stays visible on white, the dark-mode accent is brightened
 * for black backgrounds, and the text/background tints keep the base hue
 * with contrast-safe saturation/brightness.
 */
fun deriveThemePalette(base: androidx.compose.ui.graphics.Color): ThemePalette {
    val hsv = base.toHsv()
    val h = hsv[0]
    val s = hsv[1]
    val isGrayish = s < 0.08f
    fun make(hue: Float, sat: Float, value: Float) = hsvColor(hue, sat, value)
    return ThemePalette(
        // visible on white: keep it saturated and not too bright
        accent = make(h, if (isGrayish) s else maxOf(s, 0.35f), hsv[2].coerceIn(0.25f, 0.8f)),
        // visible on black: bright, softened saturation
        accentDark = make(h, minOf(s, 0.45f), maxOf(hsv[2], 0.75f)),
        // near-white with a whisper of the hue
        background = make(h, s * 0.07f, 0.985f),
        // dark shade of the hue for body text on the tinted background
        onBackground = make(h, if (isGrayish) s else minOf(maxOf(s * 0.7f, 0.35f), 0.65f), 0.30f),
        // soft tinted gray for text on black
        onBackgroundDark = make(h, minOf(s * 0.4f, 0.2f), 0.72f),
    )
}

/**
 * Border edge treatment, independent of the color theme and of the fill.
 * widthDp is the stroke weight; frameRadius applies to dialog window frames
 * and floating panels, itemRadius to in-content bordered items (the radii
 * also shape the fill when the border is NONE).
 * Persisted by ordinal; only append new entries.
 */
enum class UiBorder(
    val widthDp: Float,
    val frameRadiusDp: Float,
    val itemRadiusDp: Float,
) {
    NONE(0f, 16f, 12f),
    // the original 1dp look
    CLASSIC(1f, 5f, 7f),
    // pill / very round
    ROUND(1f, 16f, 14f),
    // sharp corners with a bold stroke
    SHARP(2f, 0f, 0f),
    // print-like double frame
    PAPER(1f, 10f, 8f),
    DASHED(1.5f, 6f, 6f),
    // postage-stamp scalloped edge
    STAMP(1f, 0f, 0f),
    // wobbly hand-drawn line
    SKETCH(1.5f, 4f, 4f),
    // thick outer frame with a hairline inner frame
    CERTIFICATE(3f, 0f, 0f),
    // rounded frame with a solid offset shadow
    STICKER(1.5f, 14f, 12f),
}

/**
 * Fill treatment for themed surfaces (dialogs, panels, selected items),
 * independent of the border. NONE is the plain theme background; GRADIENT
 * uses the adjustable angle/level. Persisted by ordinal; only append.
 */
enum class UiFill {
    NONE, TONAL, GRADIENT,
    // repeating patterns drawn in a soft accent blend
    STRIPES, DOTS, GRAPH, RULED, CROSSHATCH,
}

/** True for the repeating-pattern fills. */
fun UiFill.isPattern(): Boolean = when (this) {
    UiFill.STRIPES, UiFill.DOTS, UiFill.GRAPH, UiFill.RULED, UiFill.CROSSHATCH -> true
    else -> false
}

// baseline accent-blend fractions of the gradient fill (the user's gradient
// level scales them)
const val GRADIENT_START_FRACTION = 0.02f
const val GRADIENT_END_FRACTION = 0.28f
