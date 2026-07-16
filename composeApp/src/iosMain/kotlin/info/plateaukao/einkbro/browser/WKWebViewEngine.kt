package info.plateaukao.einkbro.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.view.Album
import kotlin.math.abs
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import info.plateaukao.einkbro.util.toByteArray
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodHTTPBasic
import platform.Foundation.NSURLAuthenticationMethodHTTPDigest
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.credentialWithUser
import platform.Foundation.serverTrust
import platform.Security.SecTrustEvaluateWithError
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIRefreshControl
import platform.UIKit.UIView
import platform.UIKit.UIUserInterfaceStyle
import platform.WebKit.WKDownload
import platform.WebKit.WKDownloadDelegateProtocol
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
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
    override val incognito: Boolean = false,
) : WebViewEngine {

    private val navigationDelegate = NavigationDelegate(this)
    private val uiDelegate = UiDelegate(this)
    internal val downloadDelegate = DownloadDelegate(this)

    // Strong refs: WKUserContentController holds message handlers weakly.
    private val messageHandlers = mutableMapOf<String, ScriptMessageHandler>()

    private val browserConfig = AppServices.config.browser
    private val refreshTarget = RefreshTarget { webView.reload() }

    val webView: WKWebView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            // Video prefs (parity Phase D): auto-fullscreen forces non-inline
            // playback; PiP is opt-in.
            allowsInlineMediaPlayback = !browserConfig.enableVideoAutoFullscreen
            allowsPictureInPictureMediaPlayback = browserConfig.enableVideoPip
            // window.open() must reach the UI delegate to open as a new tab.
            preferences.javaScriptCanOpenWindowsAutomatically = true
            // Private browsing: a non-persistent store leaves nothing on disk
            // (cookies, cache, local storage all vanish when it's released).
            if (incognito) websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        },
    ).apply {
        navigationDelegate = this@WKWebViewEngine.navigationDelegate
        UIDelegate = this@WKWebViewEngine.uiDelegate
        allowsBackForwardNavigationGestures = true
        // Our own long-press link menu replaces the native peek/preview.
        allowsLinkPreview = false
        // Pull-to-refresh (parity Phase D), opt-out via enablePullToRefresh.
        if (browserConfig.enablePullToRefresh) {
            val refreshControl = UIRefreshControl()
            refreshControl.addTarget(
                refreshTarget,
                action = platform.darwin.sel_registerName("onRefresh"),
                forControlEvents = UIControlEventValueChanged,
            )
            scrollView.refreshControl = refreshControl
        }
    }

    override fun loadUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        notifyStarted()
    }

    override fun loadFile(path: String) {
        val fileUrl = NSURL.fileURLWithPath(path)
        // Grant read access to the containing directory (webarchive/resources).
        val dirUrl = fileUrl.URLByDeletingLastPathComponent ?: fileUrl
        webView.loadFileURL(fileUrl, allowingReadAccessToURL = dirUrl)
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

    override fun addMessageHandler(name: String, handler: (String) -> Unit) {
        // Re-registering the same name would raise; drop the previous one first.
        if (messageHandlers.containsKey(name)) {
            webView.configuration.userContentController.removeScriptMessageHandlerForName(name)
        }
        val messageHandler = ScriptMessageHandler(handler)
        messageHandlers[name] = messageHandler
        webView.configuration.userContentController.addScriptMessageHandler(messageHandler, name)
    }

    override fun setUserAgent(userAgent: String?) {
        webView.customUserAgent = userAgent
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        // Applies to future navigations in this web view (same as Android, which
        // reloads on a JS toggle).
        webView.configuration.defaultWebpagePreferences.allowsContentJavaScript = enabled
    }

    override fun setAdBlockEnabled(enabled: Boolean) {
        val controller = webView.configuration.userContentController
        val list = ContentBlocker.compiledList ?: return
        if (enabled) controller.addContentRuleList(list)
        else controller.removeContentRuleList(list)
    }

    override fun setDarkMode(dark: Boolean?) {
        // overrideUserInterfaceStyle drives prefers-color-scheme in the page.
        webView.setOverrideUserInterfaceStyle(
            when (dark) {
                true -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
                false -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                null -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            }
        )
    }

    override fun setZoomEnabled(enabled: Boolean) {
        webView.scrollView.pinchGestureRecognizer?.enabled = enabled
    }

    private var multitouchHandler: ((MultitouchDirection) -> Unit)? = null
    private var panTarget: TwoFingerPanTarget? = null

    override fun setMultitouchSwipeHandler(handler: ((MultitouchDirection) -> Unit)?) {
        multitouchHandler = handler
        if (handler != null && panTarget == null) {
            // A two-finger pan recognizer (not a swipe recognizer, which is too
            // velocity-picky) reads its net translation on end and maps it to a
            // direction. The scrollView's own pan is capped to one finger so
            // single-finger scrolling is untouched and two fingers are ours.
            val target = TwoFingerPanTarget(webView) { dir -> multitouchHandler?.invoke(dir) }
            panTarget = target
            val pan = UIPanGestureRecognizer(
                target = target,
                action = platform.darwin.sel_registerName("onPan:"),
            )
            pan.minimumNumberOfTouches = 2.convert()
            pan.maximumNumberOfTouches = 2.convert()
            pan.delegate = target
            webView.addGestureRecognizer(pan)
            webView.scrollView.panGestureRecognizer.maximumNumberOfTouches = 1.convert()
        }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        webView.evaluateJavaScript(script) { result, _ ->
            callback?.invoke(result?.toString())
        }
    }

    override fun createPdf(callback: (ByteArray?) -> Unit) {
        webView.createPDFWithConfiguration(null) { data, _ ->
            callback(data?.toByteArray())
        }
    }

    override fun createWebArchive(callback: (ByteArray?) -> Unit) {
        webView.createWebArchiveDataWithCompletionHandler { data, _ ->
            callback((data as? NSData)?.toByteArray())
        }
    }

    override fun startDownload(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        webView.startDownloadUsingRequest(NSURLRequest.requestWithURL(nsUrl)) { download ->
            download?.delegate = downloadDelegate
        }
    }

    override fun pause() {
        // WKWebView pauses rendering off-window on its own; media keeps playing,
        // which matches EinkBro's background-tab behavior.
    }

    override fun resume() {}

    override fun destroy() {
        webView.navigationDelegate = null
        webView.UIDelegate = null
        messageHandlers.keys.forEach {
            webView.configuration.userContentController.removeScriptMessageHandlerForName(it)
        }
        messageHandlers.clear()
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
        // End the pull-to-refresh spinner once the load completes.
        webView.scrollView.refreshControl?.endRefreshing()
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

    internal fun reportNewWindow(url: String) = listener.onNewWindowRequested(this, url)

    internal fun reportAuthChallenge(request: AuthRequest) =
        listener.onAuthChallenge(this, request)

    internal fun reportSslError(request: SslErrorRequest) = listener.onSslError(this, request)

    internal fun reportJsDialog(request: JsDialogRequest) = listener.onJsDialog(this, request)

    internal fun reportDownloadStarted(fileName: String) =
        listener.onDownloadStarted(this, fileName)

    internal fun reportDownloadFinished(fileName: String, path: String?) =
        listener.onDownloadFinished(this, fileName, path)

    internal fun reportLoadError(description: String) = listener.onLoadError(this, description)
}

// Of the same-selector-family navigation callbacks (didStart/didCommit/
// didFinish/didFail…, all (WKWebView, WKNavigation?)), only ONE per Kotlin
// signature can be implemented — K/N turns the rest into conflicting
// overloads. didFinish and didFailProvisional are each their family's pick.
@OptIn(ExperimentalForeignApi::class)
private class NavigationDelegate(
    private val engine: WKWebViewEngine,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        engine.notifyFinished()
    }

    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        engine.notifyFailed()
        // -999 = cancelled (new load superseding), 102 = frame load interrupted
        // (fires when a response is diverted to a download) — neither is an error.
        if (withError.code != -999L && withError.code != 102L) {
            engine.reportLoadError(withError.localizedDescription)
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val scheme = url?.scheme?.lowercase()
        if (url != null && scheme != null && scheme !in WEB_SCHEMES) {
            // mailto:, tel:, app store, custom app schemes → hand off to the OS.
            UIApplication.sharedApplication.openURL(
                url, options = emptyMap<Any?, Any?>(), completionHandler = null,
            )
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        val response = decidePolicyForNavigationResponse.response
        val disposition = (response as? NSHTTPURLResponse)
            ?.allHeaderFields?.get("Content-Disposition") as? String
        val isAttachment = disposition?.startsWith("attachment", ignoreCase = true) == true
        if (!decidePolicyForNavigationResponse.canShowMIMEType || isAttachment) {
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyDownload)
        } else {
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
        }
    }

    override fun webView(
        webView: WKWebView,
        navigationResponse: WKNavigationResponse,
        didBecomeDownload: WKDownload,
    ) {
        didBecomeDownload.delegate = engine.downloadDelegate
    }

    override fun webView(
        webView: WKWebView,
        navigationAction: WKNavigationAction,
        didBecomeDownload: WKDownload,
    ) {
        didBecomeDownload.delegate = engine.downloadDelegate
    }

    override fun webView(
        webView: WKWebView,
        didReceiveAuthenticationChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val space = didReceiveAuthenticationChallenge.protectionSpace
        when (space.authenticationMethod) {
            NSURLAuthenticationMethodServerTrust -> {
                val trust = space.serverTrust
                if (trust != null && SecTrustEvaluateWithError(trust, null)) {
                    completionHandler(
                        NSURLSessionAuthChallengePerformDefaultHandling, null,
                    )
                } else {
                    engine.reportSslError(SslErrorRequest(space.host) { proceed ->
                        if (proceed && trust != null) {
                            completionHandler(
                                NSURLSessionAuthChallengeUseCredential,
                                NSURLCredential.credentialForTrust(trust),
                            )
                        } else {
                            completionHandler(
                                NSURLSessionAuthChallengeCancelAuthenticationChallenge, null,
                            )
                        }
                    })
                }
            }

            NSURLAuthenticationMethodHTTPBasic, NSURLAuthenticationMethodHTTPDigest -> {
                if (didReceiveAuthenticationChallenge.previousFailureCount > 0) {
                    completionHandler(
                        NSURLSessionAuthChallengeCancelAuthenticationChallenge, null,
                    )
                    return
                }
                engine.reportAuthChallenge(AuthRequest(space.host) { credentials ->
                    if (credentials != null) {
                        completionHandler(
                            NSURLSessionAuthChallengeUseCredential,
                            NSURLCredential.credentialWithUser(
                                credentials.first,
                                credentials.second,
                                NSURLCredentialPersistence.NSURLCredentialPersistenceForSession,
                            ),
                        )
                    } else {
                        completionHandler(
                            NSURLSessionAuthChallengeCancelAuthenticationChallenge, null,
                        )
                    }
                })
            }

            else -> completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        }
    }
}

private val WEB_SCHEMES = setOf("http", "https", "file", "about", "blob", "data")

/** UIRefreshControl target: the ObjC action fires [onRefresh] on pull-down. */
private class RefreshTarget(private val onRefresh: () -> Unit) : NSObject() {
    @ObjCAction
    fun onRefresh() = onRefresh.invoke()
}

/** Two-finger pan target (parity Phase F multitouch): on gesture end, the net
 *  translation is reduced to the dominant-axis [MultitouchDirection]. */
@OptIn(ExperimentalForeignApi::class)
private class TwoFingerPanTarget(
    private val view: UIView,
    private val onSwipe: (MultitouchDirection) -> Unit,
) : NSObject(), UIGestureRecognizerDelegateProtocol {
    // Recognize alongside WKWebView's own recognizers rather than requiring
    // them to fail, so a two-finger pan is seen even over web content.
    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
    ): Boolean = true

    @ObjCAction
    fun onPan(recognizer: UIPanGestureRecognizer) {
        if (recognizer.state != UIGestureRecognizerStateEnded) return
        val dir = recognizer.translationInView(view).useContents {
            val threshold = 40.0
            when {
                abs(x) < threshold && abs(y) < threshold -> null
                abs(x) > abs(y) -> if (x > 0) MultitouchDirection.RIGHT else MultitouchDirection.LEFT
                else -> if (y > 0) MultitouchDirection.DOWN else MultitouchDirection.UP
            }
        }
        if (dir != null) onSwipe(dir)
    }
}

/** Popups/new windows → host new-tab; JS alert/confirm/prompt → host dialog. */
private class UiDelegate(
    private val engine: WKWebViewEngine,
) : NSObject(), WKUIDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        val url = forNavigationAction.request.URL?.absoluteString
        if (!url.isNullOrBlank()) engine.reportNewWindow(url)
        // Returning null: the "window" opens as a regular tab instead.
        return null
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptAlertPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: () -> Unit,
    ) {
        engine.reportJsDialog(
            JsDialogRequest(JsDialogType.ALERT, runJavaScriptAlertPanelWithMessage, null) { _, _ ->
                completionHandler()
            }
        )
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (Boolean) -> Unit,
    ) {
        engine.reportJsDialog(
            JsDialogRequest(
                JsDialogType.CONFIRM, runJavaScriptConfirmPanelWithMessage, null,
            ) { confirmed, _ ->
                completionHandler(confirmed)
            }
        )
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt: String,
        defaultText: String?,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (String?) -> Unit,
    ) {
        engine.reportJsDialog(
            JsDialogRequest(
                JsDialogType.PROMPT, runJavaScriptTextInputPanelWithPrompt, defaultText,
            ) { confirmed, text ->
                completionHandler(if (confirmed) text.orEmpty() else null)
            }
        )
    }
}

/** Saves engine downloads under Documents/downloads and reports progress. */
internal class DownloadDelegate(
    private val engine: WKWebViewEngine,
) : NSObject(), WKDownloadDelegateProtocol {

    // WKDownload identity → destination path (delegate callbacks share it).
    private val paths = mutableMapOf<WKDownload, String>()

    override fun download(
        download: WKDownload,
        decideDestinationUsingResponse: NSURLResponse,
        suggestedFilename: String,
        completionHandler: (NSURL?) -> Unit,
    ) {
        val dir = FileStore.dirPath("downloads")
        if (dir == null) {
            completionHandler(null)
            return
        }
        var path = "$dir/$suggestedFilename"
        var attempt = 1
        while (FileStore.exists(path)) {
            path = "$dir/${attempt}_$suggestedFilename"
            attempt++
        }
        paths[download] = path
        engine.reportDownloadStarted(suggestedFilename)
        completionHandler(NSURL.fileURLWithPath(path))
    }

    override fun downloadDidFinish(download: WKDownload) {
        val path = paths.remove(download)
        engine.reportDownloadFinished(path?.substringAfterLast('/').orEmpty(), path)
    }

    override fun download(
        download: WKDownload,
        didFailWithError: NSError,
        resumeData: NSData?,
    ) {
        val path = paths.remove(download)
        engine.reportDownloadFinished(path?.substringAfterLast('/') ?: "download", null)
    }
}

// Routes window.webkit.messageHandlers.<name>.postMessage(payload) to Kotlin.
// The body is an NSString for JSON payloads; toString() yields the raw JSON.
private class ScriptMessageHandler(
    private val callback: (String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        callback(didReceiveScriptMessage.body?.toString() ?: "")
    }
}

actual fun createWebViewEngine(
    album: Album,
    listener: WebViewEngineListener,
    incognito: Boolean,
): WebViewEngine = WKWebViewEngine(album, listener, incognito)

@Composable
actual fun WebViewHost(engine: WebViewEngine, modifier: Modifier) {
    UIKitView(
        factory = { (engine as WKWebViewEngine).webView },
        modifier = modifier,
    )
}
