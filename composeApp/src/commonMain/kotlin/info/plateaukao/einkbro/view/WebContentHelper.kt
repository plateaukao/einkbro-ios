package info.plateaukao.einkbro.view

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.EinkImageMode
import info.plateaukao.einkbro.preference.FontType
import info.plateaukao.einkbro.preference.HighlightStyle
import info.plateaukao.einkbro.preference.TranslationTextStyle
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Per-tab content pipeline: the iOS port of WebViewReaderHelper +
 * WebViewJsBridge (reader/vertical/fonts/filters subset). All scrolling math
 * runs in the page's CSS pixels via JS, so no native scale conversions.
 */
class WebContentHelper(
    private val engine: WebViewEngine,
    private val config: ConfigManager = AppServices.config,
) {
    var isReaderModeOn = false
        private set
    var isVerticalRead = false
        private set
    private var verticalActivatedReaderMode = false
    private var verticalLineAdvanceCssPx = 0f
    private var isInvertOn = false

    // --- lifecycle -------------------------------------------------------

    /** New document: reader state is gone with the old DOM; styles re-apply. */
    fun onPageLoaded() {
        isReaderModeOn = false
        isVerticalRead = false
        verticalActivatedReaderMode = false
        verticalLineAdvanceCssPx = 0f
        isTranslateByParagraph = false
        updateCssStyle()
    }

    // --- CSS slots -------------------------------------------------------

    @OptIn(ExperimentalEncodingApi::class)
    fun updateCssSlot(slot: String, css: String) {
        val encoded = Base64.encode(css.encodeToByteArray())
        val js = Assets.get("update_css_slot.js")
            .replace("__SLOT_ID__", slot)
            .replace("__CSS_B64__", encoded)
        engine.evaluateJavascript(js)
    }

    fun clearCssSlot(slot: String) = updateCssSlot(slot, "")

    // Each asset here is already a self-contained IIFE, so it evaluates as-is.
    private fun evaluateJsFile(fileName: String, callback: ((String?) -> Unit)? = null) {
        engine.evaluateJavascript(Assets.get(fileName), callback)
    }

    private fun setViewportContent(content: String) {
        engine.evaluateJavascript(
            Assets.get("set_viewport_content.js").replace("__VIEWPORT_CONTENT__", content)
        )
    }

    // --- reader mode -----------------------------------------------------

    fun toggleReaderMode(isVertical: Boolean = false) {
        isReaderModeOn = !isReaderModeOn
        if (isReaderModeOn) {
            isVerticalRead = isVertical
            updateCssSlot(
                CSS_SLOT_READER,
                Assets.get(if (isVertical) "verticalReaderview.css" else "readerview.css"),
            )
            updateCssSlot(
                CSS_SLOT_VERTICAL,
                if (isVertical) Assets.get("vertical_layout.css") else "",
            )
            val readabilityLib =
                Assets.get("MozReadability.js") + "\n" + Assets.get("jsonld_article.js")
            engine.evaluateJavascript(readabilityLib) {
                replaceWithReaderModeBody {
                    if (isVertical) applyVerticalTextProcessing()
                    else updateReaderSettingsStyle()
                }
            }
            updateCssStyle()
        } else {
            isVerticalRead = false
            disableReaderMode()
            updateCssStyle()
        }
    }

    fun toggleVerticalRead() {
        isVerticalRead = !isVerticalRead
        if (isVerticalRead) {
            if (isReaderModeOn) {
                // Reader content already showing: swap in vertical styling
                // without re-running Readability on the replaced body.
                verticalActivatedReaderMode = false
                updateCssSlot(CSS_SLOT_READER, Assets.get("verticalReaderview.css"))
                updateCssSlot(CSS_SLOT_VERTICAL, Assets.get("vertical_layout.css"))
                applyVerticalTextProcessing()
            } else {
                verticalActivatedReaderMode = true
                isVerticalRead = false // toggleReaderMode(true) sets it again
                isReaderModeOn = false
                toggleReaderMode(true)
            }
        } else {
            // process_text_nodes.js rewrites the DOM irreversibly; rebuild the
            // body from the pre-reader cache, then optionally re-enter the
            // horizontal reader (same dance as Android — see WebViewReaderHelper).
            val restoreHorizontalReader = !verticalActivatedReaderMode
            isVerticalRead = true // so toggleReaderMode() tears down from vertical state
            isReaderModeOn = true
            toggleReaderMode()
            if (restoreHorizontalReader) {
                toggleReaderMode(false)
            }
        }
    }

    private fun replaceWithReaderModeBody(done: () -> Unit) {
        val keepExtra = config.display.readerKeepExtraContent
        val options = if (keepExtra) {
            "{classesToPreserve: preservedClasses, overwriteImgSrc: true, keepExtraContent: true}"
        } else {
            "{classesToPreserve: preservedClasses, overwriteImgSrc: true}"
        }
        val js = Assets.get("replace_reader_body.js")
            .replace("__INLINE_CODE_STYLES__", if (keepExtra) "inlineCodeStyles();" else "")
            .replace("__READABILITY_OPTIONS__", options)
        engine.evaluateJavascript(js) { done() }
    }

    private fun disableReaderMode() {
        clearCssSlot(CSS_SLOT_READER)
        clearCssSlot(CSS_SLOT_VERTICAL)
        clearCssSlot(CSS_SLOT_READER_SETTINGS)
        setViewportContent(VIEWPORT_DEFAULT)
        engine.evaluateJavascript(Assets.get("disable_reader_mode.js"))
    }

    private fun applyVerticalTextProcessing() {
        updateReaderSettingsStyle()
        evaluateJsFile("process_text_nodes.js") {
            measureVerticalLineAdvance {
                jumpToTop()
            }
        }
    }

    private fun measureVerticalLineAdvance(then: () -> Unit = {}) {
        evaluateJsFile("measure_line_advance.js") { value ->
            // JS reports CSS px; iOS paging also runs in CSS px — no scaling.
            verticalLineAdvanceCssPx = value?.trim('"')?.toFloatOrNull() ?: 0f
            then()
        }
    }

    /** Reader layout (margin, line spacing, two-column) in its own slot. */
    fun updateReaderSettingsStyle() {
        if (!isReaderModeOn) return

        val padding = config.display.paddingForReaderMode
        val lineHeight = formatOneDecimal(config.display.readerLineSpacing / 10.0)
        val twoColumn = !isVerticalRead && config.display.readerTwoColumnInLandscape
        val css = StringBuilder()
        css.append("body.mozac-readerview-body { padding: ${padding}px !important; }\n")
        css.append(
            ".mozac-readerview-body .mozac-readerview-content p, " +
                ".mozac-readerview-body .mozac-readerview-content li " +
                "{ line-height: $lineHeight !important; }\n"
        )
        if (twoColumn) {
            css.append(
                """
                @media screen and (orientation: landscape) {
                  body.mozac-readerview-body {
                    margin: 0 !important;
                    height: 100vh;
                    box-sizing: border-box;
                    overflow-x: auto;
                    overflow-y: hidden;
                    column-count: 2;
                    column-gap: ${padding * 2}px;
                    column-fill: auto;
                  }
                }
                """.trimIndent()
            )
        }
        updateCssSlot(CSS_SLOT_READER_SETTINGS, css.toString())
        setViewportContent(if (twoColumn) VIEWPORT_FIXED_SCALE else VIEWPORT_DEFAULT)
        if (isVerticalRead) measureVerticalLineAdvance()
    }

    // --- main style slot (fonts, colors, filters, text size) --------------

    fun updateCssStyle() {
        val url = engine.currentUrl().orEmpty()
        val fontType =
            if (isReaderModeOn) config.display.readerFontType else config.getFontType(url)
        val isBlackFont = config.getBlackFontStyle(url)
        val isBoldFont = config.getBoldFontStyle(url)
        val boldness = config.getFontBoldness(url)
        val textSize =
            if (isReaderModeOn) config.display.readerFontSize else config.getFontSize(url)

        // Font CSS first: its @import rules must precede any other rule.
        val fontCss = when (fontType) {
            FontType.SYSTEM_DEFAULT -> ""
            FontType.SERIF -> SERIF_FONT_CSS
            FontType.GOOGLE_SERIF -> NOTO_SANS_SERIF_FONT_CSS
            FontType.CUSTOM -> "" // custom TTF needs the URL-scheme handler (later phase)
            FontType.TC_IANSUI -> IANSUI_FONT_CSS
            FontType.JA_MINCHO -> JA_MINCHO_FONT_CSS
            FontType.KO_GAMJA -> KO_GAMJA_FONT_CSS
        }

        // Android scales text natively via WebSettings.textZoom; WKWebView has
        // no equivalent, so text size rides in the same CSS slot.
        val textSizeCss =
            if (textSize != 100) "html { -webkit-text-size-adjust: $textSize% !important; }\n"
            else ""

        val cssStyle = fontCss +
                textSizeCss +
                (if (isBlackFont) MAKE_TEXT_BLACK_CSS else "") +
                (if (config.whiteBackground(url)) WHITE_BACKGROUND_CSS else "") +
                (if (isBoldFont) BOLD_FONT_CSS.replace("value", "$boldness") else "") +
                einkImageFilterCss() +
                (if (isInvertOn) INVERT_CSS else "") +
                config.getCustomCss(url).orEmpty()
        // Empty blob clears the slot — that's how styles turn off without reload.
        updateCssSlot(CSS_SLOT_MAIN, cssStyle)
    }

    fun toggleInvertColor() {
        isInvertOn = !isInvertOn
        updateCssStyle()
    }

    private fun einkImageFilterCss(): String {
        if (config.display.einkImageMode != EinkImageMode.FAST) return ""
        val strength = config.display.einkImageAdjustment.strength
        if (strength <= 0) return ""
        val t = strength / 100.0
        return "img { filter: brightness(${formatThreeDecimals(1.0 + 0.15 * t)}) " +
                "contrast(${formatThreeDecimals(1.0 + 0.2 * t)}) " +
                "saturate(${formatThreeDecimals(1.0 + 0.8 * t)}) !important; }"
    }

    // --- text highlight (Phase 5) ----------------------------------------

    /**
     * Wraps the current text selection in a styled highlight span. The style
     * (color/underline) comes from the shared preference, matching Android's
     * WebViewJsBridge.highlightTextSelection. highlight.css lives in its own
     * slot so it survives the reader/main style churn.
     */
    fun highlightSelection() {
        val className = when (config.display.highlightStyle) {
            HighlightStyle.UNDERLINE -> "highlight_underline"
            HighlightStyle.BACKGROUND_YELLOW -> "highlight_yellow"
            HighlightStyle.BACKGROUND_GREEN -> "highlight_green"
            HighlightStyle.BACKGROUND_BLUE -> "highlight_blue"
            HighlightStyle.BACKGROUND_PINK -> "highlight_pink"
        }
        updateCssSlot(CSS_SLOT_HIGHLIGHT, Assets.get("highlight.css"))
        engine.evaluateJavascript(
            Assets.get("highlight_selection.js").replace("__HIGHLIGHT_CLASS__", className)
        )
    }

    // --- translation (Phase 6) --------------------------------------------

    /** Whether in-place paragraph translation is active on the current page. */
    var isTranslateByParagraph = false
        private set

    /**
     * Paragraph translation (Android's translateByParagraphInPlace): marks
     * text blocks, then text_node_monitor.js requests a translation for each
     * visible block through the einkbroGetTranslation bridge and inserts it
     * in a styled sibling paragraph below the original.
     */
    fun translateByParagraph() {
        val textBlockStyle = when (config.translation.translationTextStyle) {
            TranslationTextStyle.NONE -> TRANSLATED_P_CSS_NONE
            TranslationTextStyle.DASHED_BORDER -> TRANSLATED_P_CSS_DASHED_BORDER
            TranslationTextStyle.VERTICAL_LINE -> TRANSLATED_P_CSS_VERTICAL_LINE
            TranslationTextStyle.GRAY -> TRANSLATED_P_CSS_GRAY
            TranslationTextStyle.BOLD -> TRANSLATED_P_CSS_BOLD
        }
        updateCssSlot(CSS_SLOT_TRANSLATION, textBlockStyle)
        startParagraphTranslation(inPlace = false)
    }

    /**
     * In-place replacement (Android's translateByParagraphInPlaceReplace):
     * the translation overwrites each block's text nodes instead of being
     * inserted below it.
     */
    fun translateInPlaceReplace() {
        startParagraphTranslation(inPlace = true)
    }

    private fun startParagraphTranslation(inPlace: Boolean) {
        engine.evaluateJavascript(Assets.get("android_interface_prelude.js"))
        engine.evaluateJavascript("window._translateInPlace = $inPlace;")
        engine.evaluateJavascript(Assets.get("translate_by_paragraph.js")) {
            engine.evaluateJavascript(Assets.get("text_node_monitor.js"))
        }
        isTranslateByParagraph = true
    }

    /**
     * Google website-translate widget (Android's addGoogleTranslation): loads
     * Google's TranslateElement in auto in-place mode and hides its "Original
     * text" balloon. `preferredTranslateLanguageString` (a comma-separated
     * Google language-code list) narrows the target-language menu when set.
     */
    fun addGoogleTranslation() {
        val languages = config.translation.preferredTranslateLanguageString.orEmpty()
        val includedLanguages =
            if (languages.isNotBlank()) "includedLanguages: '$languages'," else ""
        val js = Assets.get("inject_google_translate.js")
            .replace("%%INCLUDED_LANGUAGES%%", includedLanguages)
        engine.evaluateJavascript(js) {
            engine.evaluateJavascript(Assets.get("hide_google_translate_popup.js"))
        }
    }

    /** Restores the original page content and stops translating. */
    fun clearTranslationElements() {
        engine.evaluateJavascript(Assets.get("clear_translation_elements.js"))
        clearCssSlot(CSS_SLOT_TRANSLATION)
        isTranslateByParagraph = false
    }

    /** Fetches the page's visible text (read-aloud, GPT summarize). */
    fun getRawText(callback: (String) -> Unit) {
        engine.evaluateJavascript(Assets.get("get_raw_text.js")) { result ->
            callback(result.orEmpty())
        }
    }

    /**
     * Captures the current page as an EPUB chapter (parity Phase I): runs
     * Readability then serializes the article to XHTML with rewritten image
     * paths. Returns the raw JSON `{title, xhtml, images:[{name,url}]}` (or
     * `{error}`); the caller parses it and fetches the images.
     */
    fun getEpubChapter(callback: (String) -> Unit) {
        val readabilityLib = Assets.get("MozReadability.js") + "\n" + Assets.get("jsonld_article.js")
        engine.evaluateJavascript(readabilityLib) {
            engine.evaluateJavascript(Assets.get("get_epub_chapter.js")) { result ->
                callback(result.orEmpty())
            }
        }
    }

    // --- find on page (parity Phase E) -----------------------------------

    /** Runs a find command (find/next/prev/clear); reports {count,index}. */
    fun findOnPage(command: String, query: String = "", callback: (Int, Int) -> Unit = { _, _ -> }) {
        val js = Assets.get("find_onpage.js")
            .replace("__CMD__", command)
            .replace("__ARG__", encodeUriComponent(query))
        engine.evaluateJavascript(js) { result ->
            val count = extractJsonInt(result, "count")
            val index = extractJsonInt(result, "index")
            callback(count, index)
        }
    }

    private fun encodeUriComponent(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() || c in "-_.!~*'()") append(c)
            else append('%').append(b.toUByte().toString(16).uppercase().padStart(2, '0'))
        }
    }

    private fun extractJsonInt(json: String?, key: String): Int {
        if (json == null) return 0
        val marker = "\"$key\":"
        val at = json.indexOf(marker)
        if (at < 0) return 0
        val start = at + marker.length
        val digits = json.substring(start).trimStart().takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    // --- audio only ------------------------------------------------------

    var isAudioOnlyOn = false
        private set

    fun toggleAudioOnly() {
        isAudioOnlyOn = !isAudioOnlyOn
        evaluateJsFile(if (isAudioOnlyOn) "audio_only_mode.js" else "audio_only_mode_off.js")
    }

    // --- paging ----------------------------------------------------------
    // One JS entry handles all three modes; matches WebViewNavigationHelper's
    // math, in CSS px. Vertical pages are anchored at the document's right
    // edge and advance in whole line-advance multiples.

    fun pageDown() = pageScroll(1)
    fun pageUp() = pageScroll(-1)

    private fun pageScroll(direction: Int) {
        val (reservePct, reservePx) = reservedOffset()
        val twoColumnConfigured = isReaderModeOn && !isVerticalRead &&
                config.display.readerTwoColumnInLandscape
        val js = Assets.get("page_scroll.js")
            .replace("__VERTICAL_SCROLL_HELPERS__", verticalScrollHelpers())
            .replace("__IS_VERTICAL_READER__", (isVerticalRead && isReaderModeOn).toString())
            .replace("__LINE_ADVANCE__", verticalLineAdvanceCssPx.toString())
            .replace("__TWO_COLUMN__", twoColumnConfigured.toString())
            .replace("__RESERVE_PCT__", reservePct.toString())
            .replace("__RESERVE_PX__", reservePx.toString())
            .replace("__DIRECTION__", direction.toString())
        engine.evaluateJavascript(js)
    }

    fun jumpToTop() {
        if (isVerticalRead) {
            // Reading start = right edge; the helpers hide the sign convention.
            engine.evaluateJavascript(verticalScrollToJs("0"))
        } else {
            engine.evaluateJavascript(Assets.get("scroll_to_top.js"))
        }
    }

    fun jumpToBottom() {
        if (isVerticalRead) {
            engine.evaluateJavascript(verticalScrollToJs("__ebMax()"))
        } else {
            engine.evaluateJavascript(Assets.get("scroll_to_bottom.js"))
        }
    }

    private fun verticalScrollHelpers(): String = Assets.get("vertical_scroll_helpers.js")

    /** Absolute vertical-rl jump; target is distance from the reading start. */
    private fun verticalScrollToJs(target: String): String =
        Assets.get("vertical_scroll_to.js")
            .replace("__VERTICAL_SCROLL_HELPERS__", verticalScrollHelpers())
            .replace("__TARGET__", target)

    private fun reservedOffset(): Pair<Double, Int> {
        val offset = config.touch.pageReservedOffsetInString
        return if (offset.endsWith('%')) {
            (offset.dropLast(1).toIntOrNull() ?: 0) / 100.0 to 0
        } else {
            0.0 to (offset.toIntOrNull() ?: 0)
        }
    }

    private fun formatOneDecimal(v: Double): String {
        val scaled = kotlin.math.round(v * 10).toInt()
        return "${scaled / 10}.${scaled % 10}"
    }

    private fun formatThreeDecimals(v: Double): String {
        val scaled = kotlin.math.round(v * 1000).toInt()
        return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
    }

    companion object {
        const val CSS_SLOT_MAIN = "main"
        const val CSS_SLOT_READER = "reader"
        const val CSS_SLOT_READER_SETTINGS = "readerSettings"
        const val CSS_SLOT_VERTICAL = "vertical"
        const val CSS_SLOT_HIGHLIGHT = "highlight"
        const val CSS_SLOT_TRANSLATION = "translation"

        // In-place translated block styling (Android's TRANSLATED_P_CSS_*).
        const val TRANSLATED_P_CSS_NONE = """
.to-translate + p:not(.translated) { display: none; }
.translated { padding: 5px; display: inline-block; line-height: 1.5; max-width: 100vw; }
"""
        const val TRANSLATED_P_CSS_GRAY = """
.to-translate + p:not(.translated) { display: none; }
.translated { color: gray; padding: 5px; display: inline-block; max-width: 100vw; line-height: 1.5; }
"""
        const val TRANSLATED_P_CSS_BOLD = """
.to-translate + p:not(.translated) { display: none; }
.translated { font-weight: bold; padding: 5px; display: inline-block; max-width: 100vw; line-height: 1.5; }
"""
        const val TRANSLATED_P_CSS_DASHED_BORDER = """
.to-translate + p:not(.translated) { display: none; }
.translated { border: 1px dashed lightgray; padding: 5px; display: inline-block; position: relative; max-width: 100vw; line-height: 1.5; }
"""
        const val TRANSLATED_P_CSS_VERTICAL_LINE = """
.to-translate + p:not(.translated) { display: none; }
.translated { padding: 2px; margin-left: 7px; display: inline-block; position: relative; max-width: 100vw; line-height: 1.5; }
.translated::before { content: ''; display: inline-block; width: 2px; height: 90%; background-color: black; position: absolute; left: -7px; }
"""
        const val VIEWPORT_DEFAULT = "width=device-width"
        const val VIEWPORT_FIXED_SCALE = "width=device-width, initial-scale=1.0, minimum-scale=1.0"

        const val SERIF_FONT_CSS = "* {\nfont-family: serif !important;\n}\n"

        const val NOTO_SANS_SERIF_FONT_CSS =
            "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+TC:wght@400&display=swap');" +
                "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+JP:wght@400&display=swap');" +
                "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@400&display=swap');" +
                "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400&display=swap');" +
                "* {\nfont-family: 'Noto Serif TC', 'Noto Serif JP', 'Noto Serif KR', 'Noto Serif SC', serif !important;\n}\n"

        const val IANSUI_FONT_CSS =
            "@import url('https://fonts.googleapis.com/css2?family=BIZ+UDPMincho&family=Iansui&display=swap');" +
                "* {\nfont-family: 'Iansui',serif !important;\n}\n"

        const val JA_MINCHO_FONT_CSS =
            "@import url('https://fonts.googleapis.com/css2?family=Shippori+Mincho:wght@400&display=swap');" +
                "* {\nfont-family: 'Shippori Mincho',serif !important;\n}\n"

        const val KO_GAMJA_FONT_CSS =
            "@import url('https://fonts.googleapis.com/css2?family=Gamja+Flower:wght@400&display=swap');" +
                "* {\nfont-family: 'Gamja Flower',serif !important;\n}\n"

        const val WHITE_BACKGROUND_CSS = """
* {
    color: #000000!important;
    border-color: #555555 !important;
    background-color: #FFFFFF !important;
}
"""

        const val MAKE_TEXT_BLACK_CSS = """
* { color: #000000 !important; }
a, a * { color: #000000 !important; }
input,select,option,button,textarea { color: #000000 !important; }
"""

        const val BOLD_FONT_CSS = "* {\nfont-weight:value !important;\n}\n" +
                "a,a * {\nfont-weight:value !important;\n}\n" +
                "input,select,option,button,textarea {\nfont-weight:value !important;\n}\n"

        const val INVERT_CSS = """
html { filter: invert(1) hue-rotate(180deg) !important; }
img, video, picture, canvas, iframe, embed, object, svg {
    filter: invert(1) hue-rotate(180deg) !important;
}
"""
    }
}
