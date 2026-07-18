package info.plateaukao.einkbro.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.view.EBToast
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.view.Album
import kotlin.math.abs
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import info.plateaukao.einkbro.database.FaviconInfo
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.decodeImageBitmap
import info.plateaukao.einkbro.util.toByteArray
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodHTTPBasic
import platform.Foundation.NSURLAuthenticationMethodHTTPDigest
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLRequest
import platform.Foundation.NSValue
import platform.UIKit.valueWithCGRect
import platform.UIKit.viewPrintFormatter
import platform.Foundation.NSURLRequestReturnCacheDataElseLoad
import platform.Foundation.setValue
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
import platform.UIKit.UIDevice
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
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
import platform.WebKit.WKNavigationTypeLinkActivated
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
 * WKWebView-backed engine. Title/url updates come from the navigation delegate;
 * load progress is polled from estimatedProgress on a timer (KVO isn't reachable
 * from Kotlin/Native), giving a real 0→1 progress bar.
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

    val webView: WKWebView = EBWKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            // Safari-complete UA: the default WKWebView UA carries no
            // "Version/x … Safari/x" suffix, which sites (x.com) read as an
            // in-app browser and escape via x-safari-https:// redirects. Only
            // the app-name part is replaced, so the platform token (iPhone vs
            // iPad) stays correct.
            applicationNameForUserAgent = "Version/" +
                UIDevice.currentDevice.systemVersion + " Mobile/15E148 Safari/604.1"
            // Video prefs (parity Phase D): auto-fullscreen forces non-inline
            // playback; PiP is opt-in.
            allowsInlineMediaPlayback = !browserConfig.enableVideoAutoFullscreen
            allowsPictureInPictureMediaPlayback = browserConfig.enableVideoPip
            // window.open() must reach the UI delegate to open as a new tab.
            preferences.javaScriptCanOpenWindowsAutomatically = true
            // Private browsing: a non-persistent store leaves nothing on disk
            // (cookies, cache, local storage all vanish when it's released).
            if (incognito) websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            // enableRemoteAccess (Android allowFileAccessFromFileURLs): WKWebView
            // has no public switch; the WebKit prefs respond to the same keys
            // via KVC. Read at engine creation, like Android's config applier.
            if (AppServices.config.browser.enableRemoteAccess) {
                preferences.setValue(true, forKey = "allowFileAccessFromFileURLs")
                setValue(true, forKey = "allowUniversalAccessFromFileURLs")
            }
        },
    ).apply {
        // shareLocation off (Android setGeolocationEnabled(false)): stub the
        // geolocation API at document start so pages can't even prompt.
        if (!AppServices.config.browser.shareLocation) {
            configuration.userContentController.addUserScript(
                WKUserScript(
                    source = GEOLOCATION_BLOCK_JS,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = false,
                )
            )
        }
    }.apply {
        navigationDelegate = this@WKWebViewEngine.navigationDelegate
        UIDelegate = this@WKWebViewEngine.uiDelegate
        allowsBackForwardNavigationGestures = true
        // Our own long-press link menu replaces the native peek/preview.
        allowsLinkPreview = false
        // Pull-to-refresh (parity Phase D), opt-out via enablePullToRefresh;
        // applyWebConfig keeps it in sync with the pref afterwards.
    }

    // Real progress bar: KVO on estimatedProgress isn't reachable from K/N, so
    // poll it with a light timer while a load is in flight. Reports the real
    // fraction (0→1) instead of the old coarse 0.15/0.6/1.0 steps.
    private var progressTimer: platform.Foundation.NSTimer? = null

    private fun startProgressTimer() {
        stopProgressTimer()
        progressTimer = platform.Foundation.NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.1,
            repeats = true,
        ) { _ ->
            val p = webView.estimatedProgress.toFloat()
            listener.onProgressChanged(this, p.coerceAtLeast(0.05f))
            if (p >= 1f) stopProgressTimer()
        }
    }

    private fun stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = null
    }

    override fun setPullToRefreshEnabled(enabled: Boolean) {
        if (enabled && webView.scrollView.refreshControl == null) {
            val refreshControl = UIRefreshControl()
            refreshControl.addTarget(
                refreshTarget,
                action = platform.darwin.sel_registerName("onRefresh"),
                forControlEvents = UIControlEventValueChanged,
            )
            webView.scrollView.refreshControl = refreshControl
        } else if (!enabled && webView.scrollView.refreshControl != null) {
            webView.scrollView.refreshControl?.removeFromSuperview()
            webView.scrollView.refreshControl = null
        }
    }

    override fun loadUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        // webLoadCacheFirst: serve from cache when available, else hit the network.
        val request = if (AppServices.config.browser.webLoadCacheFirst) {
            NSMutableURLRequest.requestWithURL(nsUrl, NSURLRequestReturnCacheDataElseLoad, 60.0)
        } else {
            NSMutableURLRequest.requestWithURL(nsUrl)
        }
        // Save-Data request header (Android EBWebView does the same; like there,
        // it applies to the main document request only).
        if (AppServices.config.browser.enableSaveData) {
            request.setValue("on", forHTTPHeaderField = "Save-Data")
        }
        // DNT is always sent, matching Android EBWebView.requestHeaders.
        request.setValue("1", forHTTPHeaderField = "DNT")
        webView.loadRequest(request)
        notifyStarted()
    }

    override fun loadHtml(html: String) {
        webView.loadHTMLString(html, baseURL = null)
        notifyStarted()
    }

    // Failed main-frame URL, so einkbro://retry can re-fetch it.
    private var errorPageFailedUrl: String? = null

    internal fun showErrorPage(failedUrl: String, reason: String) {
        errorPageFailedUrl = failedUrl
        val html = Assets.get("error_page.html")
        // The page reads ?url=/&reason= from location.search; a custom-scheme
        // base URL carries them and lets location.href='einkbro://retry' fire
        // the navigation delegate (which re-fetches errorPageFailedUrl).
        val base = NSURL.URLWithString(
            "einkbro://error/?url=" + failedUrl.encodeForQuery() +
                "&reason=" + friendlyReason(reason).encodeForQuery()
        )
        webView.loadHTMLString(html, baseURL = base)
    }

    internal fun retryErrorPage(): Boolean {
        val url = errorPageFailedUrl ?: return false
        errorPageFailedUrl = null
        loadUrl(url)
        return true
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

    override fun setImageBlockEnabled(enabled: Boolean) {
        val controller = webView.configuration.userContentController
        val list = ContentBlocker.imageBlockList ?: return
        if (enabled) controller.addContentRuleList(list)
        else controller.removeContentRuleList(list)
    }

    override fun setCookieBlockEnabled(enabled: Boolean) {
        val controller = webView.configuration.userContentController
        val list = ContentBlocker.cookieBlockList ?: return
        if (enabled) controller.addContentRuleList(list)
        else controller.removeContentRuleList(list)
    }

    override fun setAnalyticsBlockEnabled(enabled: Boolean) {
        val controller = webView.configuration.userContentController
        val list = ContentBlocker.analyticsBlockList ?: return
        if (enabled) controller.addContentRuleList(list)
        else controller.removeContentRuleList(list)
    }

    override fun setInspectable(enabled: Boolean) {
        // isInspectable is iOS 16.4+; the binding guards older targets at runtime.
        webView.inspectable = enabled
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

    private var scrollDelegate: ScrollObserver? = null

    override fun setScrollChangeHandler(handler: ((Int, Int) -> Unit)?) {
        if (handler == null) {
            webView.scrollView.delegate = null
            scrollDelegate = null
            return
        }
        val observer = ScrollObserver(handler)
        scrollDelegate = observer // strong ref; scrollView.delegate is weak
        webView.scrollView.delegate = observer
    }

    private var multitouchHandler: ((MultitouchDirection) -> Unit)? = null
    private var panTarget: TwoFingerPanTarget? = null

    override fun setMultitouchSwipeHandler(handler: ((MultitouchDirection) -> Unit)?) {
        multitouchHandler = handler
        // Only install the two-finger pan recognizer when multitouch paging is
        // actually enabled — it competes with WKWebView's native pinch-to-zoom
        // and breaks it otherwise (and multitouch is off by default). Reads the
        // pref at engine creation, like the other web prefs.
        if (handler != null && panTarget == null &&
            AppServices.config.touch.isMultitouchEnabled
        ) {
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
        // Paginated PDF at the configured paper size (Android prints through
        // PrintManager with PrintAttributes.MediaSize; UIPrintPageRenderer is
        // the iOS equivalent). Falls back to WKWebView's single-page snapshot
        // if the renderer produces nothing.
        val (w, h) = when (AppServices.config.display.pdfPaperSize) {
            info.plateaukao.einkbro.preference.PaperSize.ISO_13 -> 595.0 to 842.0   // A4
            info.plateaukao.einkbro.preference.PaperSize.SIZE_10 -> 420.0 to 595.0  // A5
            info.plateaukao.einkbro.preference.PaperSize.ISO_67 -> 315.0 to 445.0   // ~6.7"
            info.plateaukao.einkbro.preference.PaperSize.SIZE_8 -> 323.0 to 459.0   // C6-ish
        }
        val renderer = platform.UIKit.UIPrintPageRenderer()
        renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAtIndex = 0)
        val paper = platform.CoreGraphics.CGRectMake(0.0, 0.0, w, h)
        val printable = platform.CoreGraphics.CGRectMake(20.0, 20.0, w - 40.0, h - 40.0)
        renderer.setValue(NSValue.valueWithCGRect(paper), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(printable), forKey = "printableRect")
        val pages = renderer.numberOfPages.toInt()
        if (pages <= 0) {
            webView.createPDFWithConfiguration(null) { data, _ -> callback(data?.toByteArray()) }
            return
        }
        val pdfData = platform.Foundation.NSMutableData()
        platform.UIKit.UIGraphicsBeginPDFContextToData(pdfData, paper, null)
        for (i in 0 until pages) {
            platform.UIKit.UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(
                i.toLong(), platform.UIKit.UIGraphicsGetPDFContextBounds(),
            )
        }
        platform.UIKit.UIGraphicsEndPDFContext()
        callback(pdfData.toByteArray())
    }

    override fun createWebArchive(callback: (ByteArray?) -> Unit) {
        webView.createWebArchiveDataWithCompletionHandler { data, _ ->
            callback((data as? NSData)?.toByteArray())
        }
    }

    override fun captureSnapshot(callback: (ByteArray?) -> Unit) {
        // Null config captures the visible viewport; the OCR endpoint wants JPEG.
        webView.takeSnapshotWithConfiguration(null) { image, _ ->
            val jpeg = (image as? UIImage)?.let { UIImageJPEGRepresentation(it, 0.9) }
            callback(jpeg?.toByteArray())
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
        stopProgressTimer()
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
        // Kick the bar visible immediately; the timer drives it from there as
        // estimatedProgress climbs.
        listener.onProgressChanged(this, 0.05f)
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
        startProgressTimer()
    }

    internal fun notifyCommitted() {
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
    }

    internal fun notifyFinished() {
        // End the pull-to-refresh spinner once the load completes.
        webView.scrollView.refreshControl?.endRefreshing()
        stopProgressTimer()
        listener.onProgressChanged(this, 1f)
        listener.onTitleChanged(this, webView.title ?: "")
        listener.onUrlChanged(this, webView.URL?.absoluteString ?: "")
        listener.onPageFinished(
            this,
            webView.URL?.absoluteString ?: "",
            webView.title ?: "",
        )
        fetchFavicon()
    }

    // Android gets favicons pushed via WebChromeClient.onReceivedIcon and routes
    // them through setAlbumCoverAndSyncDb (album cover + favicons table).
    // WKWebView never pushes icons, so resolve the page's icon URL via JS,
    // fetch it, and feed the same two sinks.
    private fun fetchFavicon() {
        val pageUrl = webView.URL?.absoluteString ?: return
        if (!pageUrl.startsWith("http")) return
        val host = Uri.parse(pageUrl).host ?: return
        evaluateJavascript(FAVICON_URL_JS) { result ->
            val iconUrl = result?.trim('"')?.takeIf { it.startsWith("http") }
                ?: return@evaluateJavascript
            val nsUrl = NSURL.URLWithString(iconUrl) ?: return@evaluateJavascript
            NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, _, _ ->
                val bytes = data?.toByteArray() ?: return@dataTaskWithURL
                val bitmap = decodeImageBitmap(bytes) ?: return@dataTaskWithURL
                dispatch_async(dispatch_get_main_queue()) {
                    album.setAlbumCover(bitmap)
                    // Divergence from Android (which always persists): incognito
                    // promises nothing on disk, so skip the favicons table there.
                    if (!incognito) {
                        AppServices.bookmarkManager.insertFaviconAsync(
                            FaviconInfo(domain = host, icon = bytes)
                        )
                    }
                }
            }.resume()
        }
    }

    internal fun notifyFailed() {
        stopProgressTimer()
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

    internal fun requestRouteLinkToSplit(url: String): Boolean =
        listener.shouldRouteLinkToSplit(this, url)

    internal fun reportUserScriptInstall(url: String) =
        listener.onUserScriptInstallRequested(this, url)
}

// Of the same-selector-family navigation callbacks (didStart/didCommit/
// didFinish/didFail…, all (WKWebView, WKNavigation?)), only ONE per Kotlin
// signature can be implemented — K/N turns the rest into conflicting
// overloads. didFinish and didFailProvisional are each their family's pick.
@OptIn(ExperimentalForeignApi::class)
private class NavigationDelegate(
    private val engine: WKWebViewEngine,
) : NSObject(), WKNavigationDelegateProtocol {

    // x-safari escape loop guard: stale x.com state (mx cookie / cached service
    // worker) can re-issue the escape right after every strip-and-reload, which
    // would otherwise ping-pong forever. Timestamps are epoch seconds.
    private var lastEscapeSeenAt = 0.0
    private var escapeSwallowCount = 0

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
            val failingUrl = withError.userInfo["NSErrorFailingURLStringKey"] as? String
                ?: webView.URL?.absoluteString
            engine.reportLoadError(withError.localizedDescription)
            // Offline/DNS/refused main-frame failures show the error page with a
            // retry button (Android WebErrorPagePresenter).
            if (failingUrl != null && failingUrl.startsWith("http")) {
                engine.showErrorPage(failingUrl, withError.localizedDescription)
            }
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val scheme = url?.scheme?.lowercase()
        // Error-page retry button: re-fetch the failed URL, never leave the app.
        if (url?.absoluteString?.startsWith("einkbro://retry") == true) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            if (!engine.retryErrorPage()) engine.reload()
            return
        }
        // Internal einkbro:// (the error page's own base URL): render it, never
        // hand it to the OS or show the leave-app dialog.
        if (scheme == "einkbro") {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        // x.com's in-app-browser escape: it rewrites navigation to
        // x-safari-https://… so Safari opens it. Strip the prefix and load the
        // real URL in this tab instead of bouncing the user out of the app.
        // If the page re-escapes right away (sticky x.com app-preference state),
        // swallow it and stay on the rendered page instead of reload-looping.
        if (scheme != null && scheme.startsWith("x-safari-")) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            val now = NSDate().timeIntervalSince1970
            val bouncing = now - lastEscapeSeenAt < ESCAPE_LOOP_WINDOW_S
            lastEscapeSeenAt = now
            if (bouncing) {
                escapeSwallowCount++
                if (escapeSwallowCount == 1) {
                    EBToast.show(AppServices.context, "Blocked a reload loop (open-in-app redirect)")
                }
                return
            }
            escapeSwallowCount = 0
            url?.absoluteString?.removePrefix("x-safari-")
                ?.takeIf { it.startsWith("http") }
                ?.let { engine.loadUrl(it) }
            return
        }
        if (url != null && scheme != null && scheme !in WEB_SCHEMES) {
            // mailto:, tel:, app store, custom app schemes: never leave the
            // app silently — sites (e.g. x.com) redirect through app schemes
            // and would bounce the user out. Ask first.
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            AppServices.dialogManager.showOkCancelDialog(
                message = "Leave EinkBro and open this link externally?\n${url.absoluteString}",
                okAction = {
                    UIApplication.sharedApplication.openURL(
                        url, options = emptyMap<Any?, Any?>(), completionHandler = null,
                    )
                },
            )
            return
        }
        // *.user.js → offer to install as a userscript instead of navigating
        // (Android NinjaWebViewClient.isUserScriptUrl/offerUserScriptInstall).
        if (url != null && url.path?.lowercase()?.endsWith(".user.js") == true) {
            engine.reportUserScriptInstall(url.absoluteString ?: "")
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            return
        }
        // Split-screen "link here" (parity Phase G): a user tap on a link routes
        // to the second pane instead of navigating this one.
        if (url != null &&
            decidePolicyForNavigationAction.navigationType == WKNavigationTypeLinkActivated &&
            engine.requestRouteLinkToSplit(url.absoluteString ?: "")
        ) {
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

/** Re-escape within this many seconds of the last x-safari sighting = a bounce
 *  loop; swallow instead of reloading. Sliding — each sighting extends it. */
private const val ESCAPE_LOOP_WINDOW_S = 5.0

// Minimal percent-encoding for the error-page query values.
private fun String.encodeForQuery(): String = buildString {
    for (c in this@encodeForQuery) {
        if (c.isLetterOrDigit() || c in "-_.~") append(c)
        else for (b in c.toString().encodeToByteArray()) {
            append('%'); append(((b.toInt() and 0xFF) shr 4).toString(16).uppercase())
            append((b.toInt() and 0x0F).toString(16).uppercase())
        }
    }
}

// WebErrorPagePresenter.friendlyReason, condensed for NSError descriptions.
private fun friendlyReason(raw: String?): String {
    if (raw.isNullOrBlank()) return "Check your connection and try again."
    val r = raw.lowercase()
    return when {
        "offline" in r || "not connected to the internet" in r ->
            "You appear to be offline. Check your Wi-Fi or mobile data."
        "hostname could not be found" in r || "not be found" in r ->
            "Couldn't find this site. Check the address and try again."
        "timed out" in r -> "The connection timed out."
        "connection was lost" in r || "reset" in r ->
            "The connection was interrupted."
        "refused" in r -> "The server refused the connection."
        else -> "Check your connection and try again."
    }
}

// navigator.geolocation stub: every request fails with PERMISSION_DENIED.
private val GEOLOCATION_BLOCK_JS = """
    (function() {
      var deny = function(success, error) {
        if (error) error({ code: 1, message: 'Geolocation disabled' });
      };
      try {
        Object.defineProperty(navigator, 'geolocation', {
          value: {
            getCurrentPosition: deny,
            watchPosition: function(s, e) { deny(s, e); return 0; },
            clearWatch: function() {},
          },
          configurable: false,
        });
      } catch (e) {}
    })();
""".trimIndent()

/**
 * WKWebView that hides the system edit menu on text selection: EinkBro shows
 * its own ActionModeMenu instead (Android suppresses the default ActionMode
 * the same way), unless Appearance → "show default action menu" is enabled.
 * paste/select stay allowed so editable fields keep working.
 */
@OptIn(ExperimentalForeignApi::class)
private class EBWKWebView(
    frame: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>,
    configuration: WKWebViewConfiguration,
) : WKWebView(frame, configuration) {
    override fun canPerformAction(
        action: kotlinx.cinterop.COpaquePointer?,
        withSender: Any?,
    ): Boolean {
        if (!AppServices.config.ui.showDefaultActionMenu && action != null) {
            val name = platform.objc.sel_getName(action)?.toKString() ?: ""
            val allowed = name == "paste:" || name == "select:" || name == "selectAll:"
            if (!allowed) return false
        }
        return super.canPerformAction(action, withSender)
    }

    // iOS 16+ builds the edit menu through UIMenuBuilder as well; items added
    // there (e.g. WebKit's "Copy Link with Highlight") never consult
    // canPerformAction and use private identifiers, so instead of guessing,
    // walk the root menu and drop every child menu except standard-edit —
    // whose cut/copy actions canPerformAction already hides, keeping paste
    // usable in editable fields.
    override fun buildMenuWithBuilder(builder: platform.UIKit.UIMenuBuilderProtocol) {
        super.buildMenuWithBuilder(builder)
        if (AppServices.config.ui.showDefaultActionMenu) return
        val root = builder.menuForIdentifier(platform.UIKit.UIMenuRoot) ?: return
        root.children.forEach { child ->
            val menu = child as? platform.UIKit.UIMenu ?: return@forEach
            if (menu.identifier != platform.UIKit.UIMenuStandardEdit) {
                builder.removeMenuForIdentifier(menu.identifier)
            }
        }
    }

}

// Last matching link wins, same as Android WebView's icon pick; absolute href
// courtesy of the DOM. Falls back to the conventional /favicon.ico.
private val FAVICON_URL_JS = """
    (function() {
      var links = document.querySelectorAll(
        "link[rel~='icon'], link[rel='shortcut icon'], link[rel='apple-touch-icon']");
      if (links.length > 0) return links[links.length - 1].href;
      return location.origin + '/favicon.ico';
    })()
""".trimIndent()

/** UIRefreshControl target: the ObjC action fires [onRefresh] on pull-down. */
private class RefreshTarget(private val onRefresh: () -> Unit) : NSObject() {
    @ObjCAction
    fun onRefresh() = onRefresh.invoke()
}


/** Streams vertical scroll deltas for the auto-hide-toolbar pref. */
@OptIn(ExperimentalForeignApi::class)
private class ScrollObserver(
    private val onScroll: (Int, Int) -> Unit,
) : NSObject(), platform.UIKit.UIScrollViewDelegateProtocol {
    private var lastY = 0.0

    override fun scrollViewDidScroll(scrollView: platform.UIKit.UIScrollView) {
        val y = scrollView.contentOffset.useContents { this.y }
        val dy = y - lastY
        lastY = y
        onScroll(dy.toInt(), y.toInt())
    }
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
