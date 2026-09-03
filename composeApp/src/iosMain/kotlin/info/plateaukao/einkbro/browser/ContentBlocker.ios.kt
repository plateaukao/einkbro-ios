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

    // Analytics/tracker fast-block (Android NinjaWebViewClient.ANALYTICS_DOMAINS).
    internal var analyticsBlockList: WKContentRuleList? = null
        private set

    actual val isReady: Boolean get() = compiledList != null

    actual fun preload(rulesJson: String, onReady: () -> Unit) {
        if (compiledList != null) {
            onReady()
            return
        }
        val store = WKContentRuleListStore.defaultStore() ?: run { onReady(); return }
        loadOrCompile(store, "einkbro-adblock", rulesJson) { list ->
            if (list != null) compiledList = list
            onReady()
        }
    }

    actual fun preloadPrivacyRules(onReady: () -> Unit) {
        val store = WKContentRuleListStore.defaultStore() ?: run { onReady(); return }
        var pending = 3
        val done = { pending--; if (pending == 0) onReady() }
        if (imageBlockList == null) {
            loadOrCompile(store, "einkbro-block-images", IMAGE_BLOCK_JSON) { list ->
                if (list != null) imageBlockList = list; done()
            }
        } else done()
        if (cookieBlockList == null) {
            loadOrCompile(store, "einkbro-block-cookies", COOKIE_BLOCK_JSON) { list ->
                if (list != null) cookieBlockList = list; done()
            }
        } else done()
        if (analyticsBlockList == null) {
            loadOrCompile(store, "einkbro-block-analytics", ANALYTICS_BLOCK_JSON) { list ->
                if (list != null) analyticsBlockList = list; done()
            }
        } else done()
    }

    /**
     * The store persists compiled lists on disk by identifier, but only a
     * lookUp reuses them; compileContentRuleListForIdentifier recompiles every
     * launch. The identifier carries a hash of the JSON, so a rule change (an
     * app update) misses the cache and compiles fresh instead of serving a
     * stale list; the superseded entries under the same base name are removed.
     */
    private fun loadOrCompile(
        store: WKContentRuleListStore,
        baseId: String,
        json: String,
        onDone: (WKContentRuleList?) -> Unit,
    ) {
        val identifier = "$baseId-" + json.hashCode().toUInt().toString(16)
        store.lookUpContentRuleListForIdentifier(identifier) { cached, _ ->
            if (cached != null) {
                onDone(cached)
                return@lookUpContentRuleListForIdentifier
            }
            store.compileContentRuleListForIdentifier(
                identifier = identifier,
                encodedContentRuleList = json,
            ) { compiled, _ ->
                onDone(compiled)
                if (compiled != null) pruneStale(store, baseId, keep = identifier)
            }
        }
    }

    private fun pruneStale(store: WKContentRuleListStore, baseId: String, keep: String) {
        store.getAvailableContentRuleListIdentifiers { identifiers ->
            identifiers.orEmpty()
                .mapNotNull { it as? String }
                .filter { it.startsWith("$baseId-") && it != keep }
                .forEach { store.removeContentRuleListForIdentifier(it) { _ -> } }
        }
    }

    private const val IMAGE_BLOCK_JSON =
        "[{\"trigger\":{\"url-filter\":\".*\",\"resource-type\":[\"image\"]}," +
            "\"action\":{\"type\":\"block\"}}]"
    private const val COOKIE_BLOCK_JSON =
        "[{\"trigger\":{\"url-filter\":\".*\"},\"action\":{\"type\":\"block-cookies\"}}]"

    // One block rule per tracker host (Android's ANALYTICS_DOMAINS). Dots are
    // escaped for the url-filter regex; substring match is the default.
    private val ANALYTICS_BLOCK_JSON: String = listOf(
        "google-analytics\\.com", "googletagmanager\\.com", "connect\\.facebook\\.net",
        "platform\\.twitter\\.com/widgets\\.js", "cdn\\.segment\\.com",
        "static\\.hotjar\\.com", "bat\\.bing\\.com", "mc\\.yandex\\.ru",
        "analytics\\.tiktok\\.com", "snap\\.licdn\\.com", "cdn\\.mouseflow\\.com",
        "cdn\\.heapanalytics\\.com",
    ).joinToString(prefix = "[", postfix = "]", separator = ",") { host ->
        "{\"trigger\":{\"url-filter\":\"$host\"},\"action\":{\"type\":\"block\"}}"
    }
}
