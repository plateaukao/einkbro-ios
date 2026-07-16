package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.BookmarkManager
import info.plateaukao.einkbro.database.ChatGptQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Room-backed port of the Android GptQueryViewModel: the saved GPT query/result
 * list, persisted in the `chat_gpt_query` table via [BookmarkManager] (Phase K).
 */
class GptQueryViewModel(
    private val bookmarkManager: BookmarkManager = AppServices.bookmarkManager,
) : ViewModel() {

    fun getGptQueries(): Flow<List<ChatGptQuery>> = bookmarkManager.getAllChatGptQueries()

    suspend fun dumpGptQueriesAsHtml(): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>GPT Queries Dump</title></head><body><h1>GPT Queries</h1>")
        bookmarkManager.getAllChatGptQueriesAsync().forEach {
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
            bookmarkManager.deleteChatGptQuery(query)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            bookmarkManager.deleteAllChatGptQueries()
        }
    }
}
