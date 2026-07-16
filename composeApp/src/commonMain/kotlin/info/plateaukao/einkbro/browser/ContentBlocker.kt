package info.plateaukao.einkbro.browser

/**
 * Compiles the bundled ad/tracker block rules into a platform content blocker
 * once at startup. On iOS this is a WKContentRuleList compiled from EasyList-
 * style JSON and applied to each WKWebView's userContentController.
 */
expect object ContentBlocker {
    /** Compile [rulesJson] (WKContentRuleList JSON) once; [onReady] fires when done. */
    fun preload(rulesJson: String, onReady: () -> Unit = {})

    /** True once the rule list has compiled and can be applied to web views. */
    val isReady: Boolean
}
