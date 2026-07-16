package info.plateaukao.einkbro.data.remote

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Fetches a page and reduces it to readable plain text (parity Phase K:
 * "summarize link" / "read link aloud"). Android runs the link through an
 * off-screen EBWebView + Readability; the iOS port fetches the HTML over Ktor
 * and strips it with a lightweight tag remover — good enough to feed an LLM
 * summary or the TTS reader without spinning up a hidden WebView.
 */
object PageContentFetcher {

    suspend fun fetchText(url: String): String? = try {
        val html = HttpClientProvider.client.get(url).bodyAsText()
        htmlToPlainText(html).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun htmlToPlainText(html: String): String {
        var s = html
        s = Regex("(?is)<script[^>]*>.*?</script>").replace(s, " ")
        s = Regex("(?is)<style[^>]*>.*?</style>").replace(s, " ")
        s = Regex("(?is)<head[^>]*>.*?</head>").replace(s, " ")
        s = Regex("(?is)<!--.*?-->").replace(s, " ")
        s = Regex("(?i)<br\\s*/?>").replace(s, "\n")
        s = Regex("(?i)</(p|div|h[1-6]|li|tr|section|article)>").replace(s, "\n")
        s = Regex("<[^>]+>").replace(s, " ")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rsquo;", "'")
            .replace("&mdash;", "—")
        s = Regex("[ \\t]+").replace(s, " ")
        s = Regex("\\n{3,}").replace(s, "\n\n")
        return s.trim()
    }
}
