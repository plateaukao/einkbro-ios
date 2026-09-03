package info.plateaukao.einkbro.view

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.caption.CaptionFetchResult
import info.plateaukao.einkbro.caption.DualCaptionProcessor
import info.plateaukao.einkbro.caption.YouTubeCaptionFetcher
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.FontType
import info.plateaukao.einkbro.preference.HighlightStyle
import info.plateaukao.einkbro.preference.TranslationTextStyle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.resolveStoredPath

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
        // Android WebContentPostProcessor: reflow text to the viewport width
        // after a pinch zoom (github.com/plateaukao/einkbro/issues/537).
        if (config.display.enableZoomTextWrapReflow) {
            evaluateJsFile("zoom-text-wrap-reflow.js")
        }
        // Per-site desktop viewport width (Android injectForcedViewportWidth):
        // force a wide viewport so desktop-mode sites lay out at full width.
        // Gated on desktop mode: in Site Settings this width is a refinement of
        // Desktop Mode (only editable while it's on, cleared together on reset),
        // but the stored value survives turning Desktop Mode off. Applying it
        // regardless then forces a wide desktop layout with a mobile UA — an
        // orphaned 1280 here pinned facebook.com to 1280px, so it overflowed the
        // screen and could pinch-zoom out below 1.0. Only honour it in desktop mode.
        val fitUrl = engine.currentUrl().orEmpty()
        val width = config.getDesktopViewportWidth(fitUrl)
        if (config.getDesktopMode(fitUrl) && width != null && width > 0) {
            engine.evaluateJavascript(
                info.plateaukao.einkbro.browser.Assets.get("force_viewport_width.js")
                    .replace("__WIDTH__", width.toString())
            )
        }
        // Force pinch-zoom on sites that set user-scalable=no / maximum-scale=1
        // (Android WebContentPostProcessor enableZoomJs). WKWebView otherwise
        // obeys the page's viewport and pinch does nothing.
        if (config.display.enableZoom && !isReaderModeOn) {
            evaluateJsFile("force_zoom.js")
        }
        updateEbookTouchMode()
    }

    /**
     * Ebook touch-area type (Android intercepts taps natively in EBWebView):
     * arm or disarm the in-page tap reporter. The einkbroEbookTap handler in
     * BrowserViewModel re-checks the pref per message, so disarming is only
     * cosmetic hygiene for pages that keep running.
     */
    fun updateEbookTouchMode() {
        if (config.touch.isEbookModeActive) {
            evaluateJsFile("ebook_touch.js")
        } else {
            engine.evaluateJavascript("window.__einkbroEbookTouchEnabled = false;")
        }
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
        updateCssSlot(CSS_SLOT_MAIN, buildMainCss(engine.currentUrl().orEmpty(), isReaderModeOn))
        updateFitWidthClip()
    }

    /**
     * The main style slot for a fresh navigation to [url], installed at
     * document start by the engine (a new document always starts with reader
     * mode off; invert persists per tab). Same CSS updateCssStyle applies at
     * page finish, so that later call finds the slot populated and skips the
     * DOM mutation.
     */
    fun documentStartCss(url: String): String = buildMainCss(url, readerMode = false)

    private fun buildMainCss(url: String, readerMode: Boolean): String {
        val fontType =
            if (readerMode) config.display.readerFontType else config.getFontType(url)
        val isBlackFont = config.getBlackFontStyle(url)
        val isBoldFont = config.getBoldFontStyle(url)
        val boldness = config.getFontBoldness(url)
        val textSize =
            if (readerMode) config.display.readerFontSize else config.getFontSize(url)

        // Font CSS first: its @import rules must precede any other rule.
        val fontCss = when (fontType) {
            FontType.SYSTEM_DEFAULT -> ""
            FontType.SERIF -> SERIF_FONT_CSS
            FontType.GOOGLE_SERIF -> NOTO_SANS_SERIF_FONT_CSS
            FontType.CUSTOM -> customFontCss(reader = readerMode)
            FontType.TC_IANSUI -> IANSUI_FONT_CSS
            FontType.JA_MINCHO -> JA_MINCHO_FONT_CSS
            FontType.KO_GAMJA -> KO_GAMJA_FONT_CSS
        }

        // Android scales text natively via WebSettings.textZoom; WKWebView has
        // no equivalent, so text size rides in the same CSS slot.
        // -webkit-text-size-adjust is NOT inherited and virtually every site
        // resets it on body, so an html-only rule never applies — hit both.
        val textSizeCss =
            if (textSize != 100)
                "html, body { -webkit-text-size-adjust: $textSize% !important; }\n"
            else ""

        // Empty blob clears the slot — that's how styles turn off without reload.
        return fontCss +
                textSizeCss +
                (if (isBlackFont) MAKE_TEXT_BLACK_CSS else "") +
                (if (config.whiteBackground(url)) WHITE_BACKGROUND_CSS else "") +
                (if (isBoldFont) BOLD_FONT_CSS.replace("value", "$boldness") else "") +
                (if (isInvertOn) INVERT_CSS else "") +
                config.getCustomCss(url).orEmpty()
    }

    /**
     * @font-face for the user's custom font (Android WebViewReaderHelper
     * .getCustomFontCss + NinjaWebViewClient.processCustomFontRequest). Android
     * serves the file by intercepting a synthetic same-origin URL; WKWebView
     * can't intercept https requests, and a custom-scheme or file: URL is
     * blocked as mixed content / cross-origin by the page, so the font rides
     * inline as a data: URL. The base64 payload is cached per file path (CJK
     * fonts run to tens of MB) and the family name is versioned by the path,
     * so switching fonts forces a refetch while repeated style updates with
     * the same font keep hitting the already-loaded face.
     */
    private fun customFontCss(reader: Boolean): String {
        val info = if (reader) config.display.readerCustomFontInfo else config.display.customFontInfo
        val storedPath = info?.url?.takeIf { it.isNotBlank() } ?: return ""
        val path = resolveStoredPath(storedPath)
        val dataUrl = customFontDataUrl(path) ?: return ""
        val version = path.hashCode().toUInt().toString(16)
        return CUSTOM_FONT_CSS
            .replace("mycustomfont", dataUrl)
            .replace("fontfamily", "fontfamily$version")
    }

    /**
     * Constrains the page to the device width in normal browsing (iOS analogue of
     * Android's `useWideViewPort = isDesktopMode`). WKWebView honours the page's
     * viewport but, when content is wider than the viewport, ignores a
     * `minimum-scale=1.0` and lets the user pinch out below 1.0 to reveal the
     * overflow — which also leaves text spilling past the right edge (seen on
     * facebook.com). Clipping the root's horizontal overflow collapses
     * documentElement.scrollWidth to the viewport width, so the fit scale is
     * exactly 1.0 (no zoom-out) and nothing overflows to the right.
     *
     * Skipped whenever horizontal scrolling is intentional: desktop mode / a
     * forced per-site viewport width (both lay out wider than the screen on
     * purpose), and reader/vertical/two-column modes (which pan sideways).
     */
    private fun updateFitWidthClip() {
        val url = engine.currentUrl().orEmpty()
        // Only desktop mode lays out wider than the screen on purpose now — the
        // forced viewport width is gated on it too (see onPageLoaded). Reader /
        // vertical / two-column pan sideways by design, so leave them alone.
        val shouldClip = !isReaderModeOn && !isVerticalRead && !config.getDesktopMode(url)
        updateCssSlot(CSS_SLOT_FIT, if (shouldClip) FIT_WIDTH_CSS else "")
    }

    fun toggleInvertColor() {
        isInvertOn = !isInvertOn
        updateCssStyle()
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
        // A prepared video transcript replaces the watch-page DOM entirely
        // (Android EBWebView.getRawTextInto does the same with dualCaption).
        // Re-check the page first, so a caller that skips prepareVideoTranscript
        // can't be served the previous video's transcript.
        dropTranscriptIfPageChanged()
        dualCaption?.let { caption ->
            val html = runCatching { DualCaptionProcessor(config).convertToHtml(caption) }.getOrNull()
            if (html != null) {
                callback(html)
                return
            }
        }
        engine.evaluateJavascript(Assets.get("get_raw_text.js")) { result ->
            callback(result.orEmpty())
        }
    }

    // --- YouTube captions (parity: Android EBWebView.prepareYoutubeCaption) ---

    // Caption/transcript JSON for the video this tab currently shows.
    private var dualCaption: String? = null
    private var dualCaptionVideoId: String? = null

    // Video id of the last YouTube page whose caption fetch came up empty, so a
    // caption-less video doesn't re-hit the network on every AI action.
    private var noCaptionVideoId: String? = null

    /**
     * Page text for the AI and TTS pipelines. On a YouTube watch page the
     * video's caption transcript is fetched first and used in place of the
     * watch-page DOM, which otherwise hands the model a wall of sidebar titles
     * and comments. Everywhere else this is plain [getRawText].
     *
     * [onGeminiTranscribe] fires just before the (minutes-long) Gemini
     * transcription fallback starts, so a caller showing its own progress UI
     * can say what is happening; callers that pass nothing get a toast.
     */
    suspend fun getRawTextWithCaption(onGeminiTranscribe: (() -> Unit)? = null): String {
        prepareVideoTranscript(onGeminiTranscribe)
        return suspendCancellableCoroutine { continuation ->
            getRawText { if (continuation.isActive) continuation.resume(it) }
        }
    }

    /**
     * Fetches (and remembers) the transcript for the video on screen, so a
     * later [getRawText] returns it. Split out of [getRawTextWithCaption] for
     * callers that time-box the DOM extraction: a Gemini transcription runs for
     * minutes and must sit outside such a bound.
     */
    /**
     * Forgets the held transcript once the tab is showing something else.
     * Keyed by video id rather than hooked to page load because YouTube is an
     * SPA: moving to the next video need not commit a new document.
     */
    private fun dropTranscriptIfPageChanged() {
        if (dualCaption == null) return
        val videoId = YouTubeCaptionFetcher.extractVideoId(engine.currentUrl().orEmpty())
        if (videoId == null || videoId != dualCaptionVideoId) {
            dualCaption = null
            dualCaptionVideoId = null
        }
    }

    suspend fun prepareVideoTranscript(onGeminiTranscribe: (() -> Unit)? = null) {
        val pageUrl = engine.currentUrl().orEmpty()
        val videoId = YouTubeCaptionFetcher.extractVideoId(pageUrl)
        dropTranscriptIfPageChanged()
        if (videoId == null || dualCaption != null || videoId == noCaptionVideoId) return

        // Callers with their own progress UI pass a notice; everyone else at
        // least gets a toast, because the Gemini fallback can take minutes with
        // no other feedback.
        val notify = onGeminiTranscribe ?: {
            EBToast.show(
                AppServices.context,
                "No captions found. Transcribing video with Gemini… this may take a few minutes.",
            )
        }
        // The full transcript is always fetched; this outer timeout only guards
        // against a hung network. Sized above the Gemini fallback's 5-minute
        // request timeout; on expiry getRawText falls back to page text.
        val result = withTimeoutOrNull(360_000) {
            YouTubeCaptionFetcher().fetchCaption(pageUrl, notify)
        } ?: CaptionFetchResult.Failed("timeout", transient = true)
        when (result) {
            is CaptionFetchResult.Captions -> {
                dualCaption = result.timedTextJson
                dualCaptionVideoId = videoId
            }

            CaptionFetchResult.None -> noCaptionVideoId = videoId

            is CaptionFetchResult.Failed -> {
                // A transient failure (rate limit, network) may work next time,
                // so don't remember it against the video.
                if (!result.transient) noCaptionVideoId = videoId
                EBToast.show(
                    AppServices.context,
                    "Video transcription failed: ${result.message}",
                )
            }
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

    companion object {
        const val CSS_SLOT_MAIN = "main"
        const val CSS_SLOT_READER = "reader"
        const val CSS_SLOT_READER_SETTINGS = "readerSettings"
        const val CSS_SLOT_VERTICAL = "vertical"
        const val CSS_SLOT_HIGHLIGHT = "highlight"
        const val CSS_SLOT_TRANSLATION = "translation"
        const val CSS_SLOT_FIT = "fitwidth"

        // Clips horizontal overflow at the root so the page can't lay out wider
        // than the viewport (see updateFitWidthClip). Both html AND body are
        // needed: on WKWebView, clipping html alone leaves documentElement's
        // scrollWidth at the content width, so the fit scale stays < 1.0.
        //
        // `clip`, NOT `hidden`: per CSS Overflow 3, `hidden` on one axis forces a
        // `visible` other axis to compute to `auto`, so `overflow-x: hidden` made
        // body a nested scroll container. On sites that give body an explicit
        // height (Threads/Instagram's virtualised feeds set one) that box then
        // owned the touch gesture with only a sliver of scrollable overflow, so a
        // drag moved the feed a little and sprang straight back. `clip` paired
        // with `visible` stays `visible` — it clips without creating a scroller.
        const val FIT_WIDTH_CSS = "html, body { overflow-x: clip !important; }"

        // In-place translated block styling (Android's TRANSLATED_P_CSS_*).
        //
        // Every rule is scoped to `p.translated`, never a bare `.translated`.
        // translate_by_paragraph.js also puts a "translated" class on <body> as a state
        // flag meaning "this page is set up for translation" — unrelated to the
        // per-placeholder class. A bare `.translated` selector matched that flag, so the
        // moment translation started the whole page picked up display:inline-block,
        // padding, a forced line-height and max-width:100vw: the body grew wider than the
        // viewport and the content visibly shifted/rescaled.
        //
        // Padding is vertical-only for the same reason. These blocks are content-box, so
        // any horizontal padding adds to `max-width: 100vw` and pushes a full-width
        // translation past the viewport edge. Translated placeholders are always <p>
        // (injectTranslateTag creates them), so the `p` prefix costs nothing and keeps the
        // flag unstyled.
        const val TRANSLATED_P_CSS_NONE = """
.to-translate + p:not(.translated) { display: none; }
p.translated { padding: 5px 0; display: inline-block; line-height: 1.5; max-width: 100vw; }
"""
        const val TRANSLATED_P_CSS_GRAY = """
.to-translate + p:not(.translated) { display: none; }
p.translated { color: gray; padding: 5px 0; display: inline-block; max-width: 100vw; line-height: 1.5; }
"""
        const val TRANSLATED_P_CSS_BOLD = """
.to-translate + p:not(.translated) { display: none; }
p.translated { font-weight: bold; padding: 5px 0; display: inline-block; max-width: 100vw; line-height: 1.5; }
"""
        // The dashed border is the point of this style, so it keeps a horizontal inset —
        // but as border-box, so the border and padding fit inside max-width.
        const val TRANSLATED_P_CSS_DASHED_BORDER = """
.to-translate + p:not(.translated) { display: none; }
p.translated { box-sizing: border-box; border: 1px dashed lightgray; padding: 5px; display: inline-block; position: relative; max-width: 100vw; line-height: 1.5; }
"""
        // margin-left leaves room for the ::before bar, so max-width has to leave room for
        // the margin too or a full-width translation overflows by exactly that 7px.
        const val TRANSLATED_P_CSS_VERTICAL_LINE = """
.to-translate + p:not(.translated) { display: none; }
p.translated { padding: 2px 0; margin-left: 7px; display: inline-block; position: relative; max-width: calc(100vw - 7px); line-height: 1.5; }
p.translated::before { content: ''; display: inline-block; width: 2px; height: 90%; background-color: black; position: absolute; left: -7px; }
"""
        const val VIEWPORT_DEFAULT = "width=device-width"
        const val VIEWPORT_FIXED_SCALE = "width=device-width, initial-scale=1.0, minimum-scale=1.0"

        const val SERIF_FONT_CSS = "* {\nfont-family: serif !important;\n}\n"

        /** Android WebViewJsBridge.CUSTOM_FONT_CSS; 'mycustomfont' / 'fontfamily' are substituted. */
        const val CUSTOM_FONT_CSS = """
            @font-face {
                 font-family: fontfamily;
                 font-weight: 400;
                 font-display: swap;
                 src: url('mycustomfont');
            }
            html body * {
              font-family: fontfamily, serif, popular-symbols, lite-glyphs-outlined, lite-glyphs-filled, snaptu-symbols !important;
            }
        """

        // path -> data: URL of the font file, so every tab's style refresh
        // doesn't re-read and re-encode the same file.
        private var fontDataUrlCache: Pair<String, String>? = null

        @OptIn(ExperimentalEncodingApi::class)
        private fun customFontDataUrl(path: String): String? {
            fontDataUrlCache?.let { (cachedPath, url) -> if (cachedPath == path) return url }
            val bytes = FileStore.readBytes(path) ?: return null
            val mime = when (path.substringAfterLast('.', "").lowercase()) {
                "otf" -> "font/otf"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> "font/ttf"
            }
            val url = "data:$mime;base64," + Base64.encode(bytes)
            fontDataUrlCache = path to url
            return url
        }

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
