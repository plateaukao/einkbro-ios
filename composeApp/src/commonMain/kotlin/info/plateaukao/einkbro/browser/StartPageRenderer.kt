package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.AppServices
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import info.plateaukao.einkbro.preference.DarkMode
import info.plateaukao.einkbro.preference.GRADIENT_END_FRACTION
import info.plateaukao.einkbro.preference.GRADIENT_START_FRACTION
import info.plateaukao.einkbro.preference.UiFill
import info.plateaukao.einkbro.preference.palette
import info.plateaukao.einkbro.view.compose.UiThemeState
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.app_name
import info.plateaukao.einkbro.resources.main_omnibox_input_hint
import info.plateaukao.einkbro.resources.toast_error
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.util.Constants
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.HostBridge
import info.plateaukao.einkbro.util.ImageStats
import info.plateaukao.einkbro.util.ImageUtil
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.blockingString
import info.plateaukao.einkbro.view.EBToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Builds and loads the built-in start page (Android BookmarkRenderer.loadStartPage):
 * the shared assets/start_page.html template with the user-curated tile grid
 * filled in, loaded against the einkbro://startpage base url so the tab is
 * saved and restored as a start page.
 */
object StartPageRenderer {
    private const val BG_MAX_DIMENSION = 1600
    // no extension: holds JPEG or PNG bytes depending on what was picked
    private const val BG_FILE_NAME = "start_page_bg"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadStartPage(engine: WebViewEngine) {
        // local trusted content; the search bridge needs js even when the
        // previous page had it blocked per-domain
        engine.setJavaScriptEnabled(true)
        scope.launch {
            engine.loadHtml(startPageContent(), baseUrl = Constants.START_PAGE_URL)
        }
    }

    fun hasBackground(): Boolean =
        backgroundFilePath()?.let { FileStore.exists(it) } == true

    fun deleteBackground() {
        backgroundFilePath()?.let { FileStore.delete(it) }
    }

    /** Picked-image flow: downscale/re-encode off the main thread, store, reload. */
    fun applyPickedBackground(engine: WebViewEngine, bytes: ByteArray) {
        scope.launch {
            val saved = withContext(Dispatchers.Default) { saveBackground(bytes) }
            if (saved) {
                loadStartPage(engine)
            } else {
                EBToast.show(null, Res.string.toast_error)
            }
        }
    }

    private fun saveBackground(bytes: ByteArray): Boolean {
        val processed = ImageUtil.processBackgroundImage(bytes, BG_MAX_DIMENSION) ?: return false
        val path = backgroundFilePath() ?: return false
        return FileStore.writeToPath(path, processed) != null
    }

    private fun backgroundFilePath(): String? =
        FileStore.documentsPath()?.let { "$it/$BG_FILE_NAME" }

    private suspend fun startPageContent(): String {
        val content = AppServices.config.startPageItems.map {
            val name = it.title.escapeHtml()
            val initial = it.title.firstOrNull()?.uppercase()?.escapeHtml() ?: "#"
            // prefer the favicon the browser already stored for this domain;
            // fall back to fetching /favicon.ico, then to the initial letter
            val iconSrc = faviconDataUri(it.url) ?: faviconIcoUrl(it.url)
            """
            <a href="${it.url}" class="tile">
                <div class="tile-icon">
                    <img src="$iconSrc" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" />
                    <span class="fallback">$initial</span>
                </div>
                <div class="tile-name">$name</div>
            </a>
            """
        }.joinToString(separator = "\n")
        val backgroundBytes = backgroundFilePath()
            ?.takeIf { FileStore.exists(it) }
            ?.let { FileStore.readBytes(it) }
        val stats = backgroundBytes?.let { ImageUtil.analyzeImage(it) }
        // with a background the image's own brightness picks the theme;
        // without one the page follows the app's dark mode
        val darkTheme = stats?.isDark ?: isAppDarkMode()
        return Assets.get("start_page.html")
            .replace("{{TITLE}}", startPageTitle().escapeHtml())
            .replace("{{COLOR_SCHEME}}", if (darkTheme || backgroundBytes != null) "dark" else "light")
            .replace("{{THEME_CLASS}}", if (darkTheme) "dark" else "")
            // a custom background image owns the look; otherwise the app theme does
            .replace("{{THEME_STYLE}}", if (backgroundBytes == null) themeStyle(darkTheme) else "")
            .replace(
                "{{BG_STYLE}}",
                backgroundBytes?.let { backgroundStyle(it, stats, darkTheme) } ?: ""
            )
            .replace("{{SEARCH_HINT}}", blockingString(Res.string.main_omnibox_input_hint))
            .replace("{{ADD_LABEL}}", blockingString(Res.string.whitelist_add))
            .replace("{{CONTENT}}", content)
    }

    fun startPageTitle(): String =
        AppServices.config.startPageTitle.ifBlank { blockingString(Res.string.app_name) }

    /**
     * CSS overrides applying the app theme to the start page (Android
     * BookmarkRenderer.themeStyle): background and text colors, accent
     * borders (search bar, tiles, wordmark), and the selected fill (tonal,
     * gradient at the chosen angle/level, or the repeating patterns) as the
     * page background.
     */
    private fun themeStyle(darkTheme: Boolean): String {
        val palette = UiThemeState.current.value.palette(UiThemeState.customColor.value)
        val inverted = UiThemeState.inverted.value
        val night = inverted || darkTheme
        fun hex(c: Color) = "#" + (c.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
        val bg = when {
            inverted -> palette.onBackground
            darkTheme -> Color.Black
            else -> palette.background
        }
        val fg = when {
            inverted -> palette.background
            darkTheme -> palette.onBackgroundDark
            else -> palette.onBackground
        }
        val accent = if (night) palette.accentDark else palette.accent
        val tonal = lerp(bg, accent, if (night) 0.16f else 0.10f)
        val line = lerp(bg, accent, if (night) 0.16f else 0.12f)
        val fill = UiThemeState.uiFill.value
        val level = UiThemeState.gradientLevel.value
        fun gl(f: Float) = (f * level / 100f).coerceIn(0f, 0.9f)
        val g1 = hex(lerp(bg, accent, gl(GRADIENT_START_FRACTION)))
        val g2 = hex(lerp(bg, accent, gl(GRADIENT_END_FRACTION)))
        // our angle: 0 = left-to-right; CSS: 0deg = to top, clockwise
        val cssAngle = (UiThemeState.gradientAngle.value + 90).mod(360)
        val bgCss = when (fill) {
            UiFill.NONE -> hex(bg)
            UiFill.TONAL -> hex(tonal)
            UiFill.GRADIENT -> "linear-gradient(${cssAngle}deg, $g1, $g2)"
            UiFill.STRIPES ->
                "repeating-linear-gradient(135deg, ${hex(bg)} 0 12.5px, ${hex(line)} 12.5px 14px)"
            UiFill.DOTS -> "radial-gradient(circle, ${hex(line)} 1.5px, ${hex(bg)} 1.6px)"
            UiFill.GRAPH ->
                "repeating-linear-gradient(to right, ${hex(line)} 0 1px, transparent 1px 16px), " +
                    "repeating-linear-gradient(to bottom, ${hex(line)} 0 1px, ${hex(bg)} 1px 16px)"
            UiFill.RULED ->
                "repeating-linear-gradient(to bottom, ${hex(bg)} 0 17px, ${hex(line)} 17px 18px)"
            UiFill.CROSSHATCH ->
                "repeating-linear-gradient(135deg, ${hex(line)} 0 1px, transparent 1px 16px), " +
                    "repeating-linear-gradient(45deg, ${hex(line)} 0 1px, ${hex(bg)} 1px 16px)"
        }
        val bgSize = if (fill == UiFill.DOTS) "background-size: 14px 14px !important;" else ""
        return """
    <style>
    body { background: $bgCss !important; $bgSize color: ${hex(fg)} !important; }
    .wordmark { color: ${hex(accent)} !important; }
    .search-wrap { border-color: ${hex(accent)} !important; background: ${hex(bg)} !important; }
    .search-bar svg { color: ${hex(fg)} !important; }
    .search-bar input { color: ${hex(fg)} !important; }
    #fakeCaret { background: ${hex(fg)} !important; }
    .sugg { border-top-color: ${hex(accent)} !important; }
    .tile { color: ${hex(fg)} !important; }
    .tile-icon { border-color: ${hex(accent)} !important; background: ${hex(bg)} !important; }
    .tile-icon svg { color: ${hex(fg)} !important; }
    .tile-icon .fallback { color: ${hex(fg)} !important; }
    </style>"""
    }

    private fun isAppDarkMode(): Boolean = when (AppServices.config.display.darkMode) {
        DarkMode.DISABLED -> false
        DarkMode.FORCE_ON -> true
        DarkMode.SYSTEM -> HostBridge.isSystemDarkMode()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun backgroundStyle(
        bytes: ByteArray,
        stats: ImageStats?,
        darkTheme: Boolean,
    ): String {
        val mime = sniffImageMime(bytes)
        val base64 = Base64.encode(bytes)
        // contain + bottom anchor: show the whole image like its thumbnail,
        // seated at the bottom of the screen away from the search bar and
        // tiles near the top. The area above continues the image's own top
        // edge color instead of showing bare page color. A halo in the page
        // color (not a solid backing) keeps the text readable over the image
        // without boxing it in.
        val fallbackEdge = if (darkTheme) "#000000" else "#ffffff"
        val topColor = stats?.topColor ?: fallbackEdge
        val halo = if (darkTheme) "#000" else "#fff"
        return """
        <style>
        html {
            background: url('data:$mime;base64,$base64') center bottom / contain no-repeat fixed $topColor;
        }
        body, body.dark { background: transparent; }
        .wordmark, .tile-name {
            text-shadow: 0 0 2px $halo, 0 0 4px $halo, 0 0 6px $halo, 0 0 10px $halo;
        }
        .tile-icon, .tile-icon .fallback { background: #fff; }
        </style>
        """
    }

    private fun faviconIcoUrl(url: String): String {
        val host = Uri.parse(url).host ?: return ""
        val scheme = if (url.startsWith("http://")) "http" else "https"
        return "$scheme://$host/favicon.ico"
    }

    // Android re-encodes the decoded bitmap to PNG; here the stored bytes are
    // the original network payload, so embed them as-is with a sniffed mime.
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun faviconDataUri(url: String): String? =
        AppServices.bookmarkManager.findFaviconBy(url)?.icon?.let { bytes ->
            "data:${sniffImageMime(bytes)};base64," + Base64.encode(bytes)
        }

    private fun sniffImageMime(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
        bytes.size > 3 && bytes[0] == '<'.code.toByte() -> "image/svg+xml"
        else -> "image/x-icon"
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
