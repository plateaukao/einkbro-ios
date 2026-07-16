package info.plateaukao.einkbro.userscript

import info.plateaukao.einkbro.browser.Assets
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.util.PlatformActions
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Native half of the userscript GM_* API (parity Phase H) — the WKScriptMessage
 * counterpart to Android's `UserScriptBridge` `@JavascriptInterface`. One
 * instance is shared across tabs; per-engine state (the registered menu
 * commands) is keyed by album id.
 *
 * The JS shim (userscript_runtime.js) posts every GM call through a single
 * `einkbroGm` string channel as `{type, ...}`; this routes each to storage, a
 * Ktor fetch (GM_xmlhttpRequest), the clipboard, a new tab, or the menu-command
 * registry, and delivers async GM_xmlhttpRequest results back via
 * `window.__einkbroGM.handleXhr`.
 */
class UserScriptBridge(
    private val manager: UserScriptManager,
    private val scope: CoroutineScope,
    private val onOpenTab: (url: String, active: Boolean) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // album.id -> (caption -> fnId), in registration order. Cleared per page load.
    private val menuByEngine = mutableMapOf<Int, LinkedHashMap<String, String>>()

    /** Installs the runtime shim and the GM message channel on a new engine. */
    fun attach(engine: WebViewEngine) {
        engine.installUserScript(Assets.get("userscript_runtime.js"), atDocumentStart = true)
        engine.addMessageHandler("einkbroGm") { body -> handle(engine, body) }
    }

    /**
     * Called when a page finishes loading: forget the previous page's menu
     * commands, then push the enabled scripts so the runtime can run the ones
     * matching this URL. WKWebView's delegate can't expose a document-start hook,
     * so every matching script (any `@run-at`) is injected here.
     */
    fun onPageFinished(engine: WebViewEngine) {
        menuByEngine[engine.album.id]?.clear()
        scope.launch {
            val js = manager.buildInjectionJs() ?: return@launch
            engine.evaluateJavascript(js)
        }
    }

    /** The menu commands (caption→fnId) registered by the current page's scripts. */
    fun menuCommands(engine: WebViewEngine): List<Pair<String, String>> =
        menuByEngine[engine.album.id]?.entries?.map { it.key to it.value }.orEmpty()

    /** Runs a GM_registerMenuCommand callback by its id. */
    fun invokeMenu(engine: WebViewEngine, fnId: String) {
        engine.evaluateJavascript("window.__einkbroGM && window.__einkbroGM.invokeMenu(${jsString(fnId)});")
    }

    private fun handle(engine: WebViewEngine, body: String) {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return
        when (obj.str("type")) {
            "set" -> scope.launch { manager.setValue(obj.longOf("scriptId"), obj.str("key"), obj.str("value")) }
            "delete" -> scope.launch { manager.deleteValue(obj.longOf("scriptId"), obj.str("key")) }
            "menu" -> {
                val map = menuByEngine.getOrPut(engine.album.id) { LinkedHashMap() }
                map[obj.str("caption")] = obj.str("fnId")
            }
            "clipboard" -> PlatformActions.copyToClipboard(obj.str("text"))
            "opentab" -> onOpenTab(obj.str("url"), obj.boolOf("active", true))
            "notify" -> println("[einkbro] GM_notification: ${obj.str("text")}")
            "xhr" -> performXhr(engine, obj)
        }
    }

    private fun performXhr(engine: WebViewEngine, obj: JsonObject) {
        val reqId = obj.str("reqId")
        val url = obj.str("url")
        val host = hostOf(url)
        if (!isConnectAllowed(host, engine.currentUrl(), obj.strList("connects"))) {
            deliverXhr(engine, reqId, "error", buildJsonObject { put("error", "@connect not allowed: $host") })
            return
        }
        val method = obj.str("method").ifBlank { "GET" }
        val data = obj["data"]?.jsonPrimitive?.contentOrNull
        val headers = obj["headers"]?.jsonObject
        scope.launch {
            try {
                val response: HttpResponse = HttpClientProvider.client.request(url) {
                    this.method = HttpMethod.parse(method)
                    headers?.forEach { (k, v) -> v.jsonPrimitive.contentOrNull?.let { header(k, it) } }
                    if (data != null) setBody(data)
                }
                val text = response.bodyAsText()
                val payload = buildJsonObject {
                    put("status", response.status.value)
                    put("statusText", response.status.description)
                    put("responseText", text)
                    put("response", text)
                    put("finalUrl", url)
                    put("readyState", 4)
                    put("responseHeaders", response.headers.entries()
                        .joinToString("\r\n") { (k, v) -> "$k: ${v.joinToString(", ")}" })
                }
                deliverXhr(engine, reqId, "load", payload)
            } catch (e: Exception) {
                deliverXhr(engine, reqId, "error", buildJsonObject { put("error", e.message ?: "network error") })
            }
        }
    }

    private fun deliverXhr(engine: WebViewEngine, reqId: String, event: String, payload: JsonObject) {
        val payloadStr = jsString(json.encodeToString(JsonObject.serializer(), payload))
        engine.evaluateJavascript(
            "window.__einkbroGM && window.__einkbroGM.handleXhr(${jsString(reqId)}, ${jsString(event)}, $payloadStr);"
        )
    }

    /** Mirrors Android: `*`, the page's own host, or a `@connect` host / `*.host`. */
    private fun isConnectAllowed(host: String, pageUrl: String?, connects: List<String>): Boolean {
        if (host.isBlank()) return false
        if (connects.contains("*")) return true
        val pageHost = pageUrl?.let { hostOf(it) }
        if (pageHost != null && pageHost.equals(host, ignoreCase = true)) return true
        return connects.any { c ->
            val allowed = c.removePrefix("*.").lowercase()
            val h = host.lowercase()
            h == allowed || h.endsWith(".$allowed")
        }
    }

    private fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    }

    // --- JsonObject readers ---
    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.longOf(key: String): Long = this[key]?.jsonPrimitive?.long ?: 0L
    private fun JsonObject.boolOf(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    /** Encodes [s] as a JS string literal (quoted, escaped) for evaluateJavascript. */
    private fun jsString(s: String): String = json.encodeToString(String.serializer(), s)
}
