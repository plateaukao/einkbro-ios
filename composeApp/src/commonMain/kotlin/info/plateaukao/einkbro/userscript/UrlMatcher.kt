package info.plateaukao.einkbro.userscript

/**
 * Converts Tampermonkey `@match` / `@include` / `@exclude` patterns into
 * **JavaScript-compatible** regex source strings. The injected runtime tests
 * them against `location.href`; [matches] runs the same test in Kotlin first so
 * a page with no matching script gets no injection at all (the sources are
 * compiled with a JS-safe escaper — Kotlin's `Regex.escape` emits `\Q…\E`,
 * which JS RegExp rejects — and are plain enough to compile on both sides).
 *
 * - `@match` follows the Chrome match-pattern grammar (scheme://host/path with `*`).
 * - `@include` is looser: a glob where `*` matches any run of chars; a value that
 *   looks like `/regex/` is treated as a raw (unanchored) regex.
 * - `@exclude` is tested as either an include or a match pattern.
 *
 * Every returned source is fully anchored (or, for the `/regex/` include form,
 * intentionally unanchored) so the runtime can uniformly use `RegExp#test`.
 */
object UrlMatcher {

    /** Regex sources for a script's `@match` patterns (uncompilable ones dropped). */
    fun matchRegexes(patterns: List<String>): List<String> = patterns.mapNotNull { matchRegex(it) }

    /** Regex sources for a script's `@include` patterns. */
    fun includeRegexes(patterns: List<String>): List<String> = patterns.map { includeRegex(it) }

    /** Regex sources for `@exclude`: each pattern as include OR match (mirrors Android). */
    fun excludeRegexes(patterns: List<String>): List<String> =
        patterns.flatMap { listOfNotNull(includeRegex(it), matchRegex(it)) }

    /**
     * Kotlin-side mirror of the runtime's `matchesUrl`: included by any match or
     * include source, then not excluded. A source Kotlin's Regex rejects (the
     * raw `/regex/` include form can use JS-only syntax) counts as a match on
     * the include side and as no match on the exclude side, so the page still
     * gets the script and the runtime makes the final call.
     */
    fun matches(url: String, matches: List<String>, includes: List<String>, excludes: List<String>): Boolean {
        if (!url.startsWith("http")) return false
        val included = testAny(matches, url, onBadRegex = true) ||
            testAny(includes, url, onBadRegex = true)
        if (!included) return false
        return !testAny(excludes, url, onBadRegex = false)
    }

    private val compiled = HashMap<String, Regex?>()

    private fun testAny(sources: List<String>, url: String, onBadRegex: Boolean): Boolean =
        sources.any { source ->
            val regex = compiled.getOrPut(source) { runCatching { Regex(source) }.getOrNull() }
            regex?.containsMatchIn(url) ?: onBadRegex
        }

    private fun matchRegex(pattern: String): String? {
        if (pattern == "*" || pattern == "<all_urls>") return "^http"
        val schemeSep = pattern.indexOf("://")
        if (schemeSep < 0) return null
        val scheme = pattern.substring(0, schemeSep)
        val rest = pattern.substring(schemeSep + 3)
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val host = rest.substring(0, slash)
        val path = rest.substring(slash)

        val schemeRegex = if (scheme == "*") "https?" else escapeLiteral(scheme)
        val hostRegex = when {
            host == "*" -> "[^/]+"
            host.startsWith("*.") -> "(?:[^/]+\\.)?" + escapeLiteral(host.substring(2))
            else -> escapeLiteral(host)
        }
        val pathRegex = globBody(path)
        // Chrome match patterns match host only; tolerate an explicit :port in the URL.
        return "^$schemeRegex://$hostRegex(?::\\d+)?$pathRegex$"
    }

    private fun includeRegex(pattern: String): String {
        // /regex/ form: use the inner regex verbatim, unanchored (containsMatchIn).
        if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            return pattern.substring(1, pattern.length - 1)
        }
        return "^${globBody(pattern)}$"
    }

    /** Escape everything except `*` (any chars) for use inside a JS RegExp. */
    private fun globBody(glob: String): String =
        glob.split("*").joinToString(".*") { escapeLiteral(it) }

    /** Backslash-escape JS regex metacharacters in a literal segment. */
    private fun escapeLiteral(s: String): String = buildString {
        for (c in s) {
            if (c in REGEX_META) append('\\')
            append(c)
        }
    }

    private const val REGEX_META = ".*+?^\${}()|[]\\/"
}
