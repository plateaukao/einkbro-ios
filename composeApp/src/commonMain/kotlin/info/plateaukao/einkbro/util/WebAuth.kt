package info.plateaukao.einkbro.util

/**
 * Platform OAuth surface for Google Drive sync. Android runs the consent page
 * as a normal browser tab; on iOS an embedded web view would be rejected by
 * Google (disallowed_useragent), so the actual uses ASWebAuthenticationSession —
 * the system auth sheet that shares Safari's cookies and intercepts the
 * custom-scheme redirect itself.
 */
expect object WebAuth {
    /**
     * Opens [url] in the platform auth session and suspends until the provider
     * redirects to [callbackScheme] (scheme only, no `://`). Returns the full
     * callback URL, or null when the user cancels or the session fails.
     */
    suspend fun authenticate(url: String, callbackScheme: String): String?
}
