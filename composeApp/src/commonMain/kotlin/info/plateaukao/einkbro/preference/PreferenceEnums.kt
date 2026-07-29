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

enum class NewTabBehavior {
    START_INPUT, SHOW_HOME, SHOW_RECENT_BOOKMARKS
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
