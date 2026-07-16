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
}
