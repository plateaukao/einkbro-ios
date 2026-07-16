package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.database.ChatGptQuery
import info.plateaukao.einkbro.util.System
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * In-memory port of the Android GptQueryViewModel (Room-backed via
 * BookmarkManager there); preloaded with sample queries for the catalog.
 */
class GptQueryViewModel : ViewModel() {

    private val queries = MutableStateFlow(
        listOf(
            ChatGptQuery(
                date = System.currentTimeMillis() - 3_600_000L,
                url = "https://en.wikipedia.org/wiki/E_Ink",
                model = "gpt-4o",
                selectedText = "Electrophoretic ink, or <<E Ink>>, is a brand of electronic paper.",
                result = "**E Ink** is a display technology that mimics ink on paper.\n\n" +
                    "* Reflective: readable in direct sunlight\n" +
                    "* Bistable: keeps the image with no power\n" +
                    "* Ideal for e-readers and low-refresh UIs",
            ).apply { id = 1 },
            ChatGptQuery(
                date = System.currentTimeMillis() - 86_400_000L,
                url = "https://github.com/plateaukao/einkbro",
                model = "gemini-2.5-flash",
                selectedText = "EinkBro: A Browser for E Ink devices",
                result = "EinkBro is an Android browser optimized for e-ink screens, " +
                    "with page-turn gestures, reader mode, and minimal animations.",
            ).apply { id = 2 },
            ChatGptQuery(
                date = System.currentTimeMillis() - 3L * 86_400_000L,
                url = "",
                model = "gpt-4.1",
                selectedText = "tategaki",
                result = "*Tategaki* is vertical Japanese writing, read top-to-bottom, right-to-left.",
            ).apply { id = 3 },
        )
    )

    fun getGptQueries(): Flow<List<ChatGptQuery>> = queries

    suspend fun dumpGptQueriesAsHtml(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>GPT Queries Dump</title></head><body><h1>GPT Queries</h1>")
        queries.value.forEach {
            sb.append("<div class=\"query\"><hr><h2>")
            sb.append(it.selectedText.replace("<<", "(").replace(">>", ")"))
            sb.append("</h2><p>")
            sb.append(it.result)
            sb.append("</p></div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    fun deleteGptQuery(query: ChatGptQuery) {
        viewModelScope.launch {
            queries.value = queries.value.filter { it.id != query.id }
        }
    }
}
