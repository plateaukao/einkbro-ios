package info.plateaukao.einkbro.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.database.Article
import info.plateaukao.einkbro.database.Highlight
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.util.System
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * In-memory port of the Android HighlightViewModel (Room-backed via
 * BookmarkManager there); preloaded with sample articles + highlights.
 */
class HighlightViewModel : ViewModel() {

    private val articles = MutableStateFlow(
        listOf(
            Article(
                title = "E Ink - Wikipedia",
                url = "https://en.wikipedia.org/wiki/E_Ink",
                date = System.currentTimeMillis() - 5L * 86_400_000L,
                tags = "",
            ).apply { id = 1 },
            Article(
                title = "The Case for E-paper Dashboards",
                url = "https://example.com/epaper-dashboards",
                date = System.currentTimeMillis() - 2L * 86_400_000L,
                tags = "",
            ).apply { id = 2 },
            Article(
                title = "Reading Modes in Mobile Browsers",
                url = "https://example.com/reader-modes",
                date = System.currentTimeMillis() - 3_600_000L,
                tags = "",
            ).apply { id = 3 },
        )
    )

    private val highlights = MutableStateFlow(
        listOf(
            Highlight(articleId = 1, content = "E Ink displays are bistable: an image persists with no power draw.")
                .apply { id = 1 },
            Highlight(articleId = 1, content = "Reflective displays remain readable in direct sunlight.")
                .apply { id = 2 },
            Highlight(articleId = 2, content = "A dashboard that refreshes once a minute is a perfect e-paper workload.")
                .apply { id = 3 },
            Highlight(articleId = 3, content = "Reader mode strips navigation chrome and re-typesets the article body.")
                .apply { id = 4 },
            Highlight(articleId = 3, content = "Custom CSS injection lets users tune contrast for e-ink panels.")
                .apply { id = 5 },
        )
    )

    fun getAllArticles(): Flow<List<Article>> = articles

    private fun getAllArticlesAsync(): List<Article> = articles.value

    suspend fun getArticle(articleId: Int): Article? =
        articles.value.firstOrNull { it.id == articleId }

    suspend fun dumpArticlesHighlightsAsHtml(): String {
        val all = getAllArticlesAsync()
        var data = ""
        all.sortedByDescending { it.date }.forEach {
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

    fun deleteArticle(articleId: Int) {
        viewModelScope.launch {
            articles.value = articles.value.filter { it.id != articleId }
            highlights.value = highlights.value.filter { it.articleId != articleId }
        }
    }

    fun getHighlightsForArticle(articleId: Int): Flow<List<Highlight>> =
        highlights.map { list -> list.filter { it.articleId == articleId } }

    fun launchUrl(context: Context, url: String) {
        IntentUnit.launchUrl(context, url)
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch {
            highlights.value = highlights.value.filter { it.id != highlight.id }
        }
    }
}
