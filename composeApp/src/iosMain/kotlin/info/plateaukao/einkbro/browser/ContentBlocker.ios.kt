package info.plateaukao.einkbro.browser

import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentRuleListStore

/**
 * iOS content blocker: compiles the adblock JSON into a [WKContentRuleList].
 * The compiled list is shared by every [WKWebViewEngine]; each web view adds or
 * removes it from its userContentController based on the per-domain adblock flag.
 */
actual object ContentBlocker {
    internal var compiledList: WKContentRuleList? = null
        private set

    // Privacy content rules (parity Phase N): block all image loads, and strip
    // cookies from every request. Added/removed per web view by applyWebConfig.
    internal var imageBlockList: WKContentRuleList? = null
        private set
    internal var cookieBlockList: WKContentRuleList? = null
        private set

    actual val isReady: Boolean get() = compiledList != null

    actual fun preload(rulesJson: String, onReady: () -> Unit) {
        if (compiledList != null) {
            onReady()
            return
        }
        val store = WKContentRuleListStore.defaultStore()
        if (store == null) {
            onReady()
            return
        }
        store.compileContentRuleListForIdentifier(
            identifier = "einkbro-adblock",
            encodedContentRuleList = rulesJson,
        ) { list, _ ->
            if (list != null) compiledList = list
            onReady()
        }
    }

    actual fun preloadPrivacyRules(onReady: () -> Unit) {
        val store = WKContentRuleListStore.defaultStore() ?: run { onReady(); return }
        var pending = 2
        val done = { pending--; if (pending == 0) onReady() }
        if (imageBlockList == null) {
            store.compileContentRuleListForIdentifier(
                identifier = "einkbro-block-images",
                encodedContentRuleList = IMAGE_BLOCK_JSON,
            ) { list, _ -> if (list != null) imageBlockList = list; done() }
        } else done()
        if (cookieBlockList == null) {
            store.compileContentRuleListForIdentifier(
                identifier = "einkbro-block-cookies",
                encodedContentRuleList = COOKIE_BLOCK_JSON,
            ) { list, _ -> if (list != null) cookieBlockList = list; done() }
        } else done()
    }

    private const val IMAGE_BLOCK_JSON =
        "[{\"trigger\":{\"url-filter\":\".*\",\"resource-type\":[\"image\"]}," +
            "\"action\":{\"type\":\"block\"}}]"
    private const val COOKIE_BLOCK_JSON =
        "[{\"trigger\":{\"url-filter\":\".*\"},\"action\":{\"type\":\"block-cookies\"}}]"
}
