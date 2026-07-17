package info.plateaukao.einkbro.search.suggestion

import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.search.SearchEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Port of Android's SearchSuggestionsRepository family. Every engine is served
 * from its OpenSearch suggestions endpoint (`["query", ["s1", "s2", ...]]`);
 * Google's XML toolbar endpoint is swapped for its `client=firefox` JSON one,
 * which returns the same data without needing an XML parser in common code.
 */
object SearchSuggestionFetcher {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(searchEngineOrdinal: String, query: String): List<String> = try {
        val body = HttpClientProvider.client.get(suggestUrl(searchEngineOrdinal, query))
            .bodyAsText()
        val array = json.parseToJsonElement(body).jsonArray
        if (array.size < 2) emptyList()
        else array[1].jsonArray.map { it.jsonPrimitive.content }
    } catch (e: Exception) {
        emptyList()
    }

    private fun suggestUrl(engineOrdinal: String, query: String): String {
        val q = query.encodeURLParameter()
        return when (engineOrdinal) {
            SearchEngine.DUCKDUCKGO.ordinal.toString() ->
                "https://duckduckgo.com/ac/?q=$q&type=list"
            SearchEngine.BING.ordinal.toString() ->
                "https://www.bing.com/osjson.hint?query=$q"
            SearchEngine.ECOSIA.ordinal.toString() ->
                "https://ac.ecosia.org/?q=$q&type=list"
            SearchEngine.STARTPAGE.ordinal.toString(),
            SearchEngine.STARTPAGE_DE.ordinal.toString() ->
                "https://www.startpage.com/suggestions?q=$q&format=opensearch"
            SearchEngine.YANDEX.ordinal.toString() ->
                "https://suggest.yandex.com/suggest-ya.cgi?v=4&part=$q"
            else -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$q"
        }
    }
}
