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

    fun loadUrl(url: String)
    fun reload()
    fun stopLoading()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean

    /** Discrete e-ink style paging; Phase 1 uses plain viewport scrolls, the
     * fix_scrolling.js mechanism replaces the body in Phase 3. */
    fun pageUp()
    fun pageDown()
    fun jumpToTop()
    fun jumpToBottom()

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)

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

expect fun createWebViewEngine(album: Album, listener: WebViewEngineListener): WebViewEngine

@Composable
expect fun WebViewHost(engine: WebViewEngine, modifier: Modifier)
