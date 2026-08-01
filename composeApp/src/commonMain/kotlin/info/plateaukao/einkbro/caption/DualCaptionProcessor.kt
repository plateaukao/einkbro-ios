package info.plateaukao.einkbro.caption

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.preference.ConfigManager
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Ktor port of Android's DualCaptionProcessor. Two jobs survive the port:
 * fetching a `timedtext` track (optionally merged with a second-language copy)
 * and rendering a track as the HTML the AI/TTS pipeline consumes.
 *
 * Android also runs this from its WebViewClient to rewrite the *player's own*
 * caption request. WKWebView can't intercept page subresources, so on iOS that
 * half lives in `dual_caption_shim.js` instead; what remains here is the
 * transcript-for-AI path driven by [YouTubeCaptionFetcher].
 */
class DualCaptionProcessor(
    private val config: ConfigManager = AppServices.config,
) {
    private val client = HttpClientProvider.client
    private val serializer = TimedText.serializer()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun processUrl(url: String): String? {
        if (!url.contains(urlWithCaption)) return null

        val rawCaption = fetch(url) ?: return null
        if (rawCaption.isEmpty()) return null

        if (config.tts.dualCaptionLocale.isEmpty()) return rawCaption

        try {
            val newUrl = "$url&tlang=${config.tts.dualCaptionLocale}"
            val newCaption = fetch(newUrl).orEmpty()
            val oldCaptionJson = json.decodeFromString(serializer, rawCaption)
            val newCaptionJson = json.decodeFromString(serializer, newCaption)

            oldCaptionJson.wsWinStyles.forEach {
                if (it.mhModeHint != null) {
                    it.mhModeHint = 0
                }
                if (it.sdScrollDir != null) {
                    it.sdScrollDir = 0
                }
            }

            oldCaptionJson.events.forEach { event ->
                val segs = event.segs
                if (segs != null && segs.isNotEmpty()) {
                    val first = segs.first()
                    first.utf8 = segs.joinToString("") { it.utf8 }

                    val newCaptionSeg =
                        newCaptionJson.events.firstOrNull { it.tStartMs == event.tStartMs }?.segs
                    if (!newCaptionSeg.isNullOrEmpty()) {
                        first.utf8 += "\n" + newCaptionSeg.joinToString("") { it.utf8 }
                    }
                    segs.clear()
                    segs.add(first)
                }
            }

            return json.encodeToString(serializer, oldCaptionJson)
        } catch (exception: Exception) {
            // Dual-language merge failed (timeout, parse error, etc.). Fall back
            // to the single-language raw caption so the transcript still works.
            return rawCaption
        }
    }

    fun convertToHtml(jsonString: String): String {
        val timedText = json.decodeFromString(serializer, jsonString)
        val sb = StringBuilder()
        sb.append("<html><head><style>body{font-size: 1.5em;}</style></head><body>")
        timedText.events.forEach { event ->
            event.segs?.forEach { segment ->
                sb.append("${segment.utf8.replace("\n\n", "<br>").replace("\n", "<br>")}<br><br>")
            }
        }
        sb.append("</body></html>")
        return sb.toString().replace("<br><br><br>", "<br><br>").replace("<br><br><br>", "<br><br>")
    }

    /**
     * Android attaches the WebView cookie jar here so the CDN sees the same
     * session that was issued the timedtext URL. The URLs this port fetches come
     * from the InnerTube ANDROID client, which serves them to anonymous callers,
     * so no cookie plumbing (which on iOS would mean an async round trip through
     * WKHTTPCookieStore) is needed.
     */
    private suspend fun fetch(url: String): String? = try {
        val response = client.get(url) {
            header("User-Agent", CAPTION_USER_AGENT)
            timeout { requestTimeoutMillis = 10_000 }
        }
        if (response.status.value == 200) response.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    companion object {
        const val urlWithCaption = "timedtext"

        private const val CAPTION_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
