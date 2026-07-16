package info.plateaukao.einkbro.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.Article
import info.plateaukao.einkbro.database.Highlight
import info.plateaukao.einkbro.unit.IntentUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Room-backed (via [BookmarkManager]) list of saved articles + highlights.
 * Holds the current rows in StateFlows and refreshes them after each mutation.
 */
class HighlightViewModel : ViewModel() {

    private val manager = AppServices.bookmarkManager

    private val articles = MutableStateFlow<List<Article>>(emptyList())
    private val highlights = MutableStateFlow<List<Highlight>>(emptyList())

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val loadedArticles = manager.getAllArticles()
            articles.value = loadedArticles
            highlights.value = loadedArticles.flatMap { manager.getHighlightsForArticle(it.id) }
        }
    }

    fun getAllArticles(): Flow<List<Article>> = articles

    suspend fun getArticle(articleId: Int): Article? =
        articles.value.firstOrNull { it.id == articleId } ?: manager.getArticle(articleId)

    fun getHighlightsForArticle(articleId: Int): Flow<List<Highlight>> =
        highlights.map { list -> list.filter { it.articleId == articleId } }

    fun deleteArticle(articleId: Int) {
        viewModelScope.launch {
            manager.deleteArticle(articleId)
            refresh()
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            manager.deleteHighlight(highlight)
            refresh()
        }
    }

    suspend fun dumpArticlesHighlightsAsHtml(): String {
        var data = ""
        articles.value.sortedByDescending { it.date }.forEach {
            data += dumpSingleArticleHighlights(it.id) + "<br/><br/>"
        }
        return data
    }

    suspend fun dumpSingleArticleHighlights(articleId: Int): String {
        val article = getArticle(articleId)
        val articleTitle = article?.title.orEmpty()
        val articleHighlights = highlights.value.filter { it.articleId == articleId }
        var data = "<h2>$articleTitle</h2><hr/>"
        data += articleHighlights.joinToString("<br/><br/>") { it.content }
        data += "<br/><br/>"
        return data
    }

    fun launchUrl(context: Context, url: String) {
        IntentUnit.launchUrl(context, url)
    }
}
