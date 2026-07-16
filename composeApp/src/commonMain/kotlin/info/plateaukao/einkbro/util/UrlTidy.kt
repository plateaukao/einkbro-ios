package info.plateaukao.einkbro.util

/**
 * URL-bar tidy helpers gated on the two Behavior prefs (parity Phase C).
 * Ports Android UrlHelper's trim + NeatURL prune, using a compact built-in
 * tracking-parameter set (the common 90%: utm_*, click IDs, share tags).
 */
object UrlTidy {

    // Prefix junk before the real scheme (pasted "Shared via… https://…").
    fun trimBeforeScheme(input: String): String {
        val https = input.indexOf("https://")
        if (https > 0) return input.substring(https)
        val http = input.indexOf("http://")
        if (http > 0) return input.substring(http)
        return input
    }

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_name", "utm_cid", "utm_reader", "utm_referrer", "utm_id",
        "fbclid", "gclid", "dclid", "gclsrc", "msclkid", "mc_eid", "mc_cid",
        "igshid", "_hsenc", "_hsmi", "vero_id", "yclid", "wickedid", "twclid",
        "oly_anon_id", "oly_enc_id", "spm", "scm", "ref_src", "ref_url",
    )

    /**
     * Removes known tracking query parameters from [url]. Preserves order of
     * the remaining params; returns [url] untouched if nothing was stripped or
     * the URL has no query.
     */
    fun pruneQueryParameters(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart)
        val base = url.substring(0, queryStart)
        val query = if (fragmentStart >= 0) {
            url.substring(queryStart + 1, fragmentStart)
        } else {
            url.substring(queryStart + 1)
        }
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""

        val kept = mutableListOf<String>()
        var stripped = false
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=').lowercase()
            if (name in TRACKING_PARAMS) stripped = true else kept.add(pair)
        }
        if (!stripped) return url
        return if (kept.isEmpty()) base + fragment
        else "$base?${kept.joinToString("&")}$fragment"
    }
}
