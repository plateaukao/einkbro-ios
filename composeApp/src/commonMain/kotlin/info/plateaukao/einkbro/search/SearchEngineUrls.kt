package info.plateaukao.einkbro.search

/**
 * Maps the persisted [SearchEngine] ordinal to a query-URL prefix. Port of
 * Android's UrlHelper.queryToUrl `when` (the ordinals must match the Android
 * SP values, which pre-date this enum's declaration order — hence the explicit
 * ordinal cases rather than iterating the enum).
 */
object SearchEngineUrls {
    private const val GOOGLE = "https://www.google.com/search?q="
    private const val DUCKDUCKGO = "https://duckduckgo.com/?q="
    private const val STARTPAGE = "https://startpage.com/do/search?query="
    private const val STARTPAGE_DE = "https://startpage.com/do/search?lui=deu&language=deutsch&query="
    private const val BING = "https://www.bing.com/search?q="
    private const val BAIDU = "https://www.baidu.com/s?wd="
    private const val QWANT = "https://www.qwant.com/?q="
    private const val ECOSIA = "https://www.ecosia.org/search?q="
    private const val YANDEX = "https://yandex.com/search/?text="
    private const val SEARX = "https://searx.me/?q="

    /**
     * Returns the search URL for [query] under the given engine [ordinal].
     * Ordinal 9 (CUSTOM) uses [customTemplate] (a `%s` template or a bare
     * prefix); [encodedQuery] must already be percent-encoded.
     */
    fun searchUrl(ordinal: Int, encodedQuery: String, customTemplate: String): String =
        when (ordinal) {
            0 -> STARTPAGE + encodedQuery
            1 -> STARTPAGE_DE + encodedQuery
            2 -> BAIDU + encodedQuery
            3 -> BING + encodedQuery
            4 -> DUCKDUCKGO + encodedQuery
            5 -> GOOGLE + encodedQuery
            6 -> SEARX + encodedQuery
            7 -> QWANT + encodedQuery
            8 -> ECOSIA + encodedQuery
            9 -> if (customTemplate.contains("%s")) customTemplate.replace("%s", encodedQuery)
            else customTemplate + encodedQuery
            10 -> YANDEX + encodedQuery
            else -> GOOGLE + encodedQuery
        }
}
