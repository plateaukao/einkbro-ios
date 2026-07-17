package info.plateaukao.einkbro.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import info.plateaukao.einkbro.view.Album

/**
 * Platform seam for the web engine (MIGRATION_PLAN.md §Target architecture).
 * On iOS the actual wraps a WKWebView; the Compose side only ever sees this
 * interface plus [WebViewHost] to embed the platform view.
 */
interface WebViewEngine {
    val album: Album

    /** Non-persistent data store (private browsing); set at creation. */
    val incognito: Boolean

    fun loadUrl(url: String)

    /** Loads an in-memory HTML string (translate-by-screen result, Phase M). */
    fun loadHtml(html: String)

    /** Loads a local file (grants sandbox read access to its directory). */
    fun loadFile(path: String)

    fun reload()
    fun stopLoading()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun currentUrl(): String?

    /** Discrete e-ink style paging; Phase 1 uses plain viewport scrolls, the
     * fix_scrolling.js mechanism replaces the body in Phase 3. */
    fun pageUp()
    fun pageDown()
    fun jumpToTop()
    fun jumpToBottom()

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)

    /** Installs a script run on every future navigation (WKUserScript on iOS). */
    fun installUserScript(source: String, atDocumentStart: Boolean)

    /**
     * Registers a JS-to-Kotlin channel: page scripts post through
     * window.webkit.messageHandlers.<name>.postMessage(payload); [handler]
     * receives the payload as a string (JSON when the JS posts an object).
     */
    fun addMessageHandler(name: String, handler: (String) -> Unit)

    // --- privacy & blocking (Phase 4) ---
    /** Overrides the user agent; null restores the platform default. */
    fun setUserAgent(userAgent: String?)

    /** Enables/disables page JavaScript for future navigations. */
    fun setJavaScriptEnabled(enabled: Boolean)

    /** Adds/removes the shared adblock content-rule list on this web view. */
    fun setAdBlockEnabled(enabled: Boolean)

    /** Blocks all image loads via a content rule (parity Phase N, !enableImages). */
    fun setImageBlockEnabled(enabled: Boolean)

    /** Strips cookies from requests via a content rule (parity Phase N, !cookies). */
    fun setCookieBlockEnabled(enabled: Boolean)

    /** Makes the web view inspectable in Safari Web Inspector (debugWebView). */
    fun setInspectable(enabled: Boolean)

    /**
     * Forces the page's `prefers-color-scheme`. dark=true makes dark-capable
     * sites render dark; null follows the system appearance.
     */
    fun setDarkMode(dark: Boolean?)

    /** Enables/disables pinch-to-zoom on the page. */
    fun setZoomEnabled(enabled: Boolean)

    /** Installs/removes the pull-to-refresh control (UIRefreshControl on iOS). */
    fun setPullToRefreshEnabled(enabled: Boolean) {}

    /** Adds/removes the analytics/tracker fast-block content rule. */
    fun setAnalyticsBlockEnabled(enabled: Boolean) {}

    /**
     * Reports vertical scrolls: (deltaY, contentOffsetY) in points. Used for
     * the auto-hide-toolbar pref (Android ChromeSetupDelegate scroll hook).
     */
    fun setScrollChangeHandler(handler: ((Int, Int) -> Unit)?) {}

    /**
     * Installs a two-finger swipe recognizer on the native web view (parity
     * Phase F multitouch). A Compose overlay can't reliably catch two-finger
     * gestures over the WKWebView interop, so this rides on native gesture
     * recognizers instead. Pass null to remove the handler.
     */
    fun setMultitouchSwipeHandler(handler: ((MultitouchDirection) -> Unit)?)

    // --- export (Phase 7) ---
    /** Renders the current page to PDF bytes (null on failure). */
    fun createPdf(callback: (ByteArray?) -> Unit)

    /** Serializes the current page to a .webarchive (offline snapshot). */
    fun createWebArchive(callback: (ByteArray?) -> Unit)

    /** Captures the visible page as JPEG bytes (translate-by-screen, Phase M). */
    fun captureSnapshot(callback: (ByteArray?) -> Unit)

    // --- downloads (parity Phase B) ---
    /** Starts an in-engine download of [url] (shares the page's cookies). */
    fun startDownload(url: String)

    fun pause()
    fun resume()
    fun destroy()
}

interface WebViewEngineListener {
    fun onTitleChanged(engine: WebViewEngine, title: String) {}
    fun onUrlChanged(engine: WebViewEngine, url: String) {}
    fun onProgressChanged(engine: WebViewEngine, progress: Float) {}
    fun onPageFinished(engine: WebViewEngine, url: String, title: String) {}

    // --- delegate depth (parity Phase B) ---
    /** window.open / target=_blank: the host should open [url] in a new tab. */
    fun onNewWindowRequested(engine: WebViewEngine, url: String) {}

    /** HTTP basic/digest auth. Implementations MUST call respond exactly once. */
    fun onAuthChallenge(engine: WebViewEngine, request: AuthRequest) {
        request.respond(null)
    }

    /** Untrusted TLS certificate. respond(true) proceeds anyway. */
    fun onSslError(engine: WebViewEngine, request: SslErrorRequest) {
        request.respond(false)
    }

    /** JS alert/confirm/prompt panel. */
    fun onJsDialog(engine: WebViewEngine, request: JsDialogRequest) {
        request.respond(false, null)
    }

    fun onDownloadStarted(engine: WebViewEngine, fileName: String) {}

    /** [path] is null when the download failed. */
    fun onDownloadFinished(engine: WebViewEngine, fileName: String, path: String?) {}

    /** Provisional navigation failed (DNS failure, connection refused, …). */
    fun onLoadError(engine: WebViewEngine, description: String) {}

    /**
     * Split-screen "link here" (parity Phase G): a user link tap in the main
     * pane. Return true to consume it (the host loads it in the second pane and
     * the main navigation is cancelled); false to navigate normally.
     */
    fun shouldRouteLinkToSplit(engine: WebViewEngine, url: String): Boolean = false

    /**
     * Navigation to a *.user.js URL (Android NinjaWebViewClient.handleUri):
     * the navigation is cancelled and the host should offer to install the
     * script (UserScriptListScreen with installUrl).
     */
    fun onUserScriptInstallRequested(engine: WebViewEngine, url: String) {}
}

enum class JsDialogType { ALERT, CONFIRM, PROMPT }

/** Direction of a two-finger swipe (parity Phase F multitouch). */
enum class MultitouchDirection { UP, DOWN, LEFT, RIGHT }

/** One-shot responder for an HTTP auth challenge (user/password or null = cancel). */
class AuthRequest(
    val host: String,
    private val onResult: (Pair<String, String>?) -> Unit,
) {
    private var done = false
    fun respond(credentials: Pair<String, String>?) {
        if (!done) {
            done = true
            onResult(credentials)
        }
    }
}

/** One-shot responder for a TLS trust failure (true = load anyway). */
class SslErrorRequest(
    val host: String,
    private val onResult: (Boolean) -> Unit,
) {
    private var done = false
    fun respond(proceed: Boolean) {
        if (!done) {
            done = true
            onResult(proceed)
        }
    }
}

/** One-shot responder for a JS alert/confirm/prompt panel. */
class JsDialogRequest(
    val type: JsDialogType,
    val message: String,
    val defaultText: String?,
    private val onResult: (confirmed: Boolean, promptText: String?) -> Unit,
) {
    private var done = false
    fun respond(confirmed: Boolean, promptText: String? = null) {
        if (!done) {
            done = true
            onResult(confirmed, promptText)
        }
    }
}

expect fun createWebViewEngine(
    album: Album,
    listener: WebViewEngineListener,
    incognito: Boolean = false,
): WebViewEngine

@Composable
expect fun WebViewHost(engine: WebViewEngine, modifier: Modifier)
