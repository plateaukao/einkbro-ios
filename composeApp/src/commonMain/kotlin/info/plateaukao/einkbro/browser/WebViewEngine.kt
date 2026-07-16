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

    // --- export (Phase 7) ---
    /** Renders the current page to PDF bytes (null on failure). */
    fun createPdf(callback: (ByteArray?) -> Unit)

    /** Serializes the current page to a .webarchive (offline snapshot). */
    fun createWebArchive(callback: (ByteArray?) -> Unit)

    fun pause()
    fun resume()
    fun destroy()
}

interface WebViewEngineListener {
    fun onTitleChanged(engine: WebViewEngine, title: String) {}
    fun onUrlChanged(engine: WebViewEngine, url: String) {}
    fun onProgressChanged(engine: WebViewEngine, progress: Float) {}
    fun onPageFinished(engine: WebViewEngine, url: String, title: String) {}
}

expect fun createWebViewEngine(
    album: Album,
    listener: WebViewEngineListener,
    incognito: Boolean = false,
): WebViewEngine

@Composable
expect fun WebViewHost(engine: WebViewEngine, modifier: Modifier)
