package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.main_omnibox_input_hint
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.util.Constants
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.blockingString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Builds and loads the built-in start page (Android BookmarkRenderer.loadStartPage):
 * the shared assets/start_page.html template with the user-curated tile grid
 * filled in, loaded against the einkbro://startpage base url so the tab is
 * saved and restored as a start page.
 */
object StartPageRenderer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadStartPage(engine: WebViewEngine) {
        // local trusted content; the search bridge needs js even when the
        // previous page had it blocked per-domain
        engine.setJavaScriptEnabled(true)
        scope.launch {
            engine.loadHtml(startPageContent(), baseUrl = Constants.START_PAGE_URL)
        }
    }

    private suspend fun startPageContent(): String {
        val content = AppServices.config.startPageItems.map {
            val name = it.title.escapeHtml()
            val initial = it.title.firstOrNull()?.uppercase()?.escapeHtml() ?: "#"
            // prefer the favicon the browser already stored for this domain;
            // fall back to fetching /favicon.ico, then to the initial letter
            val iconSrc = faviconDataUri(it.url) ?: faviconIcoUrl(it.url)
            """
            <a href="${it.url}" class="tile">
                <div class="tile-icon">
                    <img src="$iconSrc" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" />
                    <span class="fallback">$initial</span>
                </div>
                <div class="tile-name">$name</div>
            </a>
            """
        }.joinToString(separator = "\n")
        return Assets.get("start_page.html")
            .replace("{{SEARCH_HINT}}", blockingString(Res.string.main_omnibox_input_hint))
            .replace("{{ADD_LABEL}}", blockingString(Res.string.whitelist_add))
            .replace("{{CONTENT}}", content)
    }

    private fun faviconIcoUrl(url: String): String {
        val host = Uri.parse(url).host ?: return ""
        val scheme = if (url.startsWith("http://")) "http" else "https"
        return "$scheme://$host/favicon.ico"
    }

    // Android re-encodes the decoded bitmap to PNG; here the stored bytes are
    // the original network payload, so embed them as-is with a sniffed mime.
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun faviconDataUri(url: String): String? =
        AppServices.bookmarkManager.findFaviconBy(url)?.icon?.let { bytes ->
            "data:${sniffImageMime(bytes)};base64," + Base64.encode(bytes)
        }

    private fun sniffImageMime(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
        bytes.size > 3 && bytes[0] == '<'.code.toByte() -> "image/svg+xml"
        else -> "image/x-icon"
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
