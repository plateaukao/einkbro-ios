package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.view.WebContentHelper
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Captures an [InitialPageSnapshot] of the page the user is viewing at the moment an
 * agent task starts (the capture block in Android's TaskMenuDelegate.runCustomTask).
 * Everything the free-form agent knows about "this page" — the system-prompt hint,
 * read_initial_page/read_initial_html, run_javascript on the live tab, and the
 * set_domain_javascript/css host — keys off this snapshot, so tasks started without
 * one can only report that the page is unavailable.
 *
 * All JS round-trips run on the main thread and are time-bounded because the
 * WKWebView callback is not guaranteed to fire on every page.
 */
object SnapshotCapture {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun capture(
        engine: WebViewEngine,
        originEngineProvider: () -> WebViewEngine?,
    ): InitialPageSnapshot = withContext(Dispatchers.Main) {
        val text = withTimeoutOrNull(EXTRACT_TIMEOUT_MS) {
            suspendCancellableCoroutine<String> { cont ->
                WebContentHelper(engine).getRawText { if (cont.isActive) cont.resume(it) }
            }
        }.orEmpty()
        InitialPageSnapshot(
            url = engine.currentUrl().orEmpty(),
            title = engine.album.albumTitle,
            text = text,
            links = parseLinks(evaluateJs(engine, LINKS_JS)),
            rawHtml = evaluateJs(engine, BODY_HTML_JS),
            originEngineProvider = originEngineProvider,
        )
    }

    private suspend fun evaluateJs(engine: WebViewEngine, code: String): String =
        withTimeoutOrNull(JS_EVAL_TIMEOUT_MS) {
            suspendCancellableCoroutine<String> { cont ->
                try {
                    engine.evaluateJavascript(code) { result ->
                        if (cont.isActive) cont.resume(result.orEmpty())
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume("")
                }
            }
        }.orEmpty()

    private fun parseLinks(raw: String): List<BrowserTools.Link> {
        if (raw.isBlank() || raw == "null") return emptyList()
        return try {
            json.decodeFromString(ListSerializer(RawLink.serializer()), raw)
                .map { BrowserTools.Link(it.text, it.href) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class RawLink(val text: String, val href: String)

    private const val EXTRACT_TIMEOUT_MS = 15_000L
    private const val JS_EVAL_TIMEOUT_MS = 10_000L

    /** WKWebView hands the JS string back verbatim, so no unescaping is needed
     *  (Android unquotes evaluateJavascript's JSON-encoded result). */
    private const val BODY_HTML_JS =
        "(function(){try{return document.body.innerHTML;}catch(e){return '';}})();"

    /** Same wire format as BrowserToolsImpl.LINKS_JS / Android jsBridge.getPageLinks(). */
    private val LINKS_JS = """
        JSON.stringify(
            Array.from(document.querySelectorAll('a[href]')).map(function(a) {
                return {
                    text: (a.innerText || a.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 200),
                    href: a.href
                };
            }).filter(function(l) { return l.text && /^https?:/.test(l.href); })
        )
    """.trimIndent()
}
