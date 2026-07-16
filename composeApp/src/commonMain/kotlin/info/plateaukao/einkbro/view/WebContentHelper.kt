package info.plateaukao.einkbro.view

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.EinkImageMode
import info.plateaukao.einkbro.preference.FontType
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

    private fun evaluateJsFile(
        fileName: String,
        withPrefix: Boolean = true,
        callback: ((String?) -> Unit)? = null,
    ) {
        val content = Assets.get(fileName)
        val js = if (withPrefix) "(function() { $content })()" else content
        engine.evaluateJavascript(js, callback)
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
        val js = """
            (function() {
                ${if (keepExtra) "inlineCodeStyles();" else ""}
                var scopedDoc = (typeof getReadabilityScopedDocument === 'function') ? getReadabilityScopedDocument() : null;
                var documentClone = scopedDoc || document.cloneNode(true);
                var article = new Readability(documentClone, $options).parse();
                document.innerHTMLCache = document.body.innerHTML;
                if (article) {
                    article.readingTime = getReadingTime(article.length, document.documentElement.lang.substring(0, 2));
                    document.body.outerHTML = createHtmlBody(article);
                    var viewport = document.getElementsByName('viewport')[0];
                    if (viewport) viewport.setAttribute('content', 'width=device-width');
                }
            })();
        """.trimIndent()
        engine.evaluateJavascript(js) { done() }
    }

    private fun disableReaderMode() {
        clearCssSlot(CSS_SLOT_READER)
        clearCssSlot(CSS_SLOT_VERTICAL)
        clearCssSlot(CSS_SLOT_READER_SETTINGS)
        setViewportContent(VIEWPORT_DEFAULT)
        engine.evaluateJavascript(
            "(function() {" +
                "document.body.innerHTML = document.innerHTMLCache;" +
                "document.body.classList.remove(\"mozac-readerview-body\");" +
                "window.scrollTo(0, 0);" +
                "})()"
        )
    }

    private fun applyVerticalTextProcessing() {
        updateReaderSettingsStyle()
        evaluateJsFile("process_text_nodes.js", withPrefix = false) {
            measureVerticalLineAdvance {
                jumpToTop()
            }
        }
    }

    private fun measureVerticalLineAdvance(then: () -> Unit = {}) {
        evaluateJsFile("measure_line_advance.js", withPrefix = false) { value ->
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
        engine.evaluateJavascript(
            "(function(dir){ ${pagingJsBody()} })($direction);"
        )
    }

    fun jumpToTop() {
        if (isVerticalRead) {
            // Reading start = right edge; see scrollFromStart helpers for the
            // vertical-rl scrollLeft sign convention handling.
            engine.evaluateJavascript(
                "(function(){ ${VERTICAL_SCROLL_HELPERS} __ebSetFromStart(0); })();"
            )
        } else {
            engine.evaluateJavascript(
                "window.scrollTo({top: 0, left: 0, behavior: 'instant'});" +
                    "window.__einkbroScrollToTop && window.__einkbroScrollToTop();"
            )
        }
    }

    fun jumpToBottom() {
        if (isVerticalRead) {
            engine.evaluateJavascript(
                "(function(){ ${VERTICAL_SCROLL_HELPERS} __ebSetFromStart(__ebMax()); })();"
            )
        } else {
            engine.evaluateJavascript(
                "window.scrollTo({top: document.documentElement.scrollHeight, left: 0, behavior: 'instant'});"
            )
        }
    }

    private fun pagingJsBody(): String {
        val (reservePct, reservePx) = reservedOffset()
        val twoColumnConfigured = isReaderModeOn && !isVerticalRead &&
                config.display.readerTwoColumnInLandscape
        return """
            if (${isVerticalRead && isReaderModeOn}) {
                $VERTICAL_SCROLL_HELPERS
                var line = $verticalLineAdvanceCssPx;
                var usable = window.innerWidth - 40;
                var step = (line > 1 && line < usable) ? Math.floor(usable / line) * line : usable;
                // dir=+1 (pageDown) advances toward the document end (leftward);
                // __ebSetFromStart hides the vertical-rl scrollLeft sign convention.
                var cur = Math.round(__ebFromStart() / step);
                __ebSetFromStart(Math.min(Math.max((cur + dir) * step, 0), __ebMax()));
                return;
            }
            if ($twoColumnConfigured && matchMedia('(orientation: landscape)').matches) {
                var w = window.innerWidth;
                var maxX = Math.max(0, document.documentElement.scrollWidth - w);
                var page = Math.round(window.scrollX / w) + dir;
                window.scrollTo({left: Math.min(Math.max(page * w, 0), maxX), top: 0, behavior: 'instant'});
                return;
            }
            if (window.__einkbroPageScroll && window.__einkbroPageScroll(dir, $reservePct, $reservePx)) return;
            var usableH = window.innerHeight * (1 - $reservePct) - $reservePx;
            window.scrollBy({top: dir * usableH, left: 0, behavior: 'instant'});
        """.trimIndent()
    }

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
        const val VIEWPORT_DEFAULT = "width=device-width"
        const val VIEWPORT_FIXED_SCALE = "width=device-width, initial-scale=1.0, minimum-scale=1.0"

        /**
         * vertical-rl scroll coordinates differ by engine convention: modern
         * WebKit uses a negative scrollLeft range (0 at the right-edge reading
         * start, -max at the end); older engines use 0..max with max at the
         * start. These helpers normalize to "distance from reading start".
         */
        val VERTICAL_SCROLL_HELPERS = """
            var __ebDoc = document.scrollingElement || document.documentElement;
            function __ebMax() { return Math.max(0, __ebDoc.scrollWidth - __ebDoc.clientWidth); }
            function __ebNegRange() {
                var o = __ebDoc.scrollLeft;
                __ebDoc.scrollLeft = -1;
                var neg = __ebDoc.scrollLeft < 0;
                __ebDoc.scrollLeft = o;
                return neg;
            }
            function __ebFromStart() {
                return __ebNegRange() ? -__ebDoc.scrollLeft : (__ebMax() - __ebDoc.scrollLeft);
            }
            function __ebSetFromStart(v) {
                __ebDoc.scrollLeft = __ebNegRange() ? -v : (__ebMax() - v);
            }
        """

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
