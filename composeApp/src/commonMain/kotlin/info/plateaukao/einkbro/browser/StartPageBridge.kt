package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.RecordType
import info.plateaukao.einkbro.search.suggestion.SearchSuggestionFetcher
import info.plateaukao.einkbro.util.Constants
import info.plateaukao.einkbro.viewmodel.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Native side of the search box on the built-in start page (Android
 * StartPageBridge): serves the same suggestion list as the URL input overlay
 * and loads the submitted text or url.
 *
 * Android attaches a @JavascriptInterface object to every page; here a
 * document-start user script maps window.einkbroStartPage onto
 * WKScriptMessageHandler channels (the android_interface_prelude.js pattern),
 * so the shared start_page.html asset runs unmodified. The shim runs on every
 * page, so each handler re-checks that the current page really is the start
 * page — arbitrary sites must not read history-based suggestions or steer
 * the tab.
 */
object StartPageBridge {
    private const val MAX_SUGGESTIONS = 8
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun attach(engine: WebViewEngine, viewModel: BrowserViewModel) {
        var searchJob: Job? = null
        engine.addMessageHandler("einkbroStartPageQuery") { payload ->
            if (engine.currentUrl() != Constants.START_PAGE_URL) return@addMessageHandler
            val message = runCatching { Json.parseToJsonElement(payload).jsonObject }
                .getOrNull() ?: return@addMessageHandler
            val query = message["query"]?.jsonPrimitive?.content ?: return@addMessageHandler
            val token = message["token"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@addMessageHandler
            searchJob?.cancel()
            searchJob = scope.launch {
                val json = suggestionsJson(viewModel, query)
                if (engine.currentUrl() != Constants.START_PAGE_URL) return@launch
                engine.evaluateJavascript(
                    "window.__einkbroSuggestions && window.__einkbroSuggestions($token, $json)"
                )
            }
        }
        engine.addMessageHandler("einkbroStartPageSubmit") { payload ->
            val trimmed = payload.trim()
            if (trimmed.isEmpty()) return@addMessageHandler
            if (engine.currentUrl() != Constants.START_PAGE_URL) return@addMessageHandler
            scope.launch {
                // loadUrlOrSearch wraps non-url text into a search engine query
                viewModel.loadUrlOrSearch(trimmed)
            }
        }
        engine.installUserScript(SHIM_JS, atDocumentStart = true)
    }

    // Mirrors the URL input overlay's list: local history filtered by the
    // query, with up to 4 engine suggestions ahead of them when enabled.
    private suspend fun suggestionsJson(viewModel: BrowserViewModel, query: String): String {
        viewModel.ensureRecordsLoaded()
        val all = viewModel.records.value
        val filtered = if (query.isEmpty()) all else all.filter {
            it.title?.contains(query, ignoreCase = true) == true ||
                it.url.contains(query, ignoreCase = true)
        }
        val config = AppServices.config
        val records = if (query.length > 1 && config.browser.enableSearchSuggestion) {
            val fromEngine = SearchSuggestionFetcher
                .fetch(config.browser.searchEngine, query)
                .take(4)
                .map {
                    info.plateaukao.einkbro.database.Record(
                        title = it, url = it, time = -1, type = RecordType.Suggestion,
                    )
                }
            fromEngine + filtered
        } else {
            filtered
        }
        return buildJsonArray {
            records.take(MAX_SUGGESTIONS).forEach { record ->
                addJsonObject {
                    put("title", kotlinx.serialization.json.JsonPrimitive(record.title ?: ""))
                    put("url", kotlinx.serialization.json.JsonPrimitive(record.url))
                    put("s", kotlinx.serialization.json.JsonPrimitive(record.type == RecordType.Suggestion))
                }
            }
        }.toString()
    }

    // Same contract as Android's addJavascriptInterface(StartPageBridge, "einkbroStartPage").
    private val SHIM_JS = """
        (function() {
          if (window.einkbroStartPage) return;
          window.einkbroStartPage = {
            querySuggestions: function(query, token) {
              try {
                window.webkit.messageHandlers.einkbroStartPageQuery.postMessage(
                  JSON.stringify({ query: String(query), token: String(token) })
                );
              } catch (e) {}
            },
            submit: function(text) {
              try {
                window.webkit.messageHandlers.einkbroStartPageSubmit.postMessage(String(text));
              } catch (e) {}
            }
          };
        })();
    """.trimIndent()
}
