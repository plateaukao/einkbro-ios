package info.plateaukao.einkbro.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import info.plateaukao.einkbro.view.Album
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * WKWebView-backed engine. Title/url/progress updates come from the
 * navigation delegate (KVO category methods aren't overridable from
 * Kotlin/Native); progress is therefore coarse — fine for e-ink UX.
 */
@OptIn(ExperimentalForeignApi::class)
class WKWebViewEngine(
    override val album: Album,
    private val listener: WebViewEngineListener,
) : WebViewEngine {

    private val navigationDelegate = NavigationDelegate(this)

    val webView: WKWebView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            allowsInlineMediaPlayback = true
        },
    ).apply {
        navigationDelegate = this@WKWebViewEngine.navigationDelegate
        allowsBackForwardNavigationGestures = true
    }

    override fun loadUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        notifyStarted()
    }

    override fun reload() {
        webView.reload()
        notifyStarted()
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun goBack() {
        webView.goBack()
    }

    override fun goForward() {
        webView.goForward()
    }

    override fun canGoBack(): Boolean = webView.canGoBack

    override fun canGoForward(): Boolean = webView.canGoForward

    override fun currentUrl(): String? = webView.URL?.absoluteString

    override fun pageUp() {
        evaluateJavascript(Assets.get("engine_scroll_by_page.js").replace("__SIGN__", "-1"))
    }

    override fun pageDown() {
        evaluateJavascript(Assets.get("engine_scroll_by_page.js").replace("__SIGN__", "1"))
    }

    override fun jumpToTop() {
        evaluateJavascript(Assets.get("scroll_to_top.js"))
    }

    override fun jumpToBottom() {
        evaluateJavascript(Assets.get("scroll_to_bottom.js"))
    }

    override fun installUserScript(source: String, atDocumentStart: Boolean) {
        val time = if (atDocumentStart) WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart
        else WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd
        webView.configuration.userContentController.addUserScript(
            WKUserScript(source = source, injectionTime = time, forMainFrameOnly = false)
        )
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        webView.evaluateJavaScript(script) { result, _ ->
            callback?.invoke(result?.toString())
        }
    }

    override fun pause() {
        // WKWebView pauses rendering off-window on its own; media keeps playing,
        // which matches EinkBro's background-tab behavior.
    }

    override fun resume() {}

    override fun destroy() {
        webView.navigationDelegate = null
        webView.stopLoading()
        webView.removeFromSuperview()
    }

    internal fun notifyStarted() {
        listener.onProgressChanged(this, 0.15f)
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
    }

    internal fun notifyCommitted() {
        listener.onProgressChanged(this, 0.6f)
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
    }

    internal fun notifyFinished() {
        listener.onProgressChanged(this, 1f)
        listener.onTitleChanged(this, webView.title ?: "")
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
        listener.onPageFinished(
            this,
            webView.URL?.absoluteString ?: "",
            webView.title ?: "",
        )
    }

    internal fun notifyFailed() {
        listener.onProgressChanged(this, 1f)
    }
}

// Only one webView(...) overload is implemented on purpose: Kotlin/Native turns
// the delegate's same-selector-family methods into conflicting overloads.
// didFinish is sufficient for Phase 1; the "started" signal fires in loadUrl().
private class NavigationDelegate(
    private val engine: WKWebViewEngine,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        engine.notifyFinished()
    }
}

actual fun createWebViewEngine(album: Album, listener: WebViewEngineListener): WebViewEngine =
    WKWebViewEngine(album, listener)

@Composable
actual fun WebViewHost(engine: WebViewEngine, modifier: Modifier) {
    UIKitView(
        factory = { (engine as WKWebViewEngine).webView },
        modifier = modifier,
    )
}
