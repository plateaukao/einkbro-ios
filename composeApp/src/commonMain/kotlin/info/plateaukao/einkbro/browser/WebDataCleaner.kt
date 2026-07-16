package info.plateaukao.einkbro.browser

/**
 * Clears WKWebView-managed browsing data (the iOS counterpart of Android's
 * ClearService). Room-backed history is cleared separately by the caller.
 */
expect object WebDataCleaner {
    fun clear(
        cache: Boolean,
        cookies: Boolean,
        localStorage: Boolean,
        onDone: () -> Unit = {},
    )
}
