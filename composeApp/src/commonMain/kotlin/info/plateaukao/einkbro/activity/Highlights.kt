package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import info.plateaukao.einkbro.database.Article
import info.plateaukao.einkbro.database.Highlight
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.articles
import info.plateaukao.einkbro.resources.back
import info.plateaukao.einkbro.resources.highlights
import info.plateaukao.einkbro.resources.ic_copy
import info.plateaukao.einkbro.resources.icon_delete
import info.plateaukao.einkbro.resources.icon_exit
import info.plateaukao.einkbro.resources.icon_export
import info.plateaukao.einkbro.unit.ShareUtil
import info.plateaukao.einkbro.util.DateFormat
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.viewmodel.HighlightViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Port of HighlightsActivity: articles list + per-article highlights, with
 * in-screen navigation between the two routes. Export-to-file flows surface
 * a toast (no file picker on the iOS catalog).
 */
@Composable
fun HighlightsScreen(onClose: () -> Unit = {}) {
    val highlightViewModel = remember { HighlightViewModel() }
    val context = LocalContext.current
    val navController: NavHostController = rememberNavController()

    MyTheme {
        val backStackEntry = navController.currentBackStackEntryAsState()
        val currentScreen = HighlightsRoute.valueOf(
            backStackEntry.value?.destination?.route?.split("/")?.first()
                ?: HighlightsRoute.RouteArticles.name
        )

        Scaffold(
            // Keep the top bar clear of the iOS status bar so back is tappable.
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                HighlightsBar(
                    currentScreen = currentScreen,
                    onClick = {
                        // Android: file picker + BackupUnit export of the dumped html.
                        EBToast.show(context, "would export ${currentScreen.name} as html")
                    },
                    navigateUp = {
                        if (navController.previousBackStackEntry != null) navController.navigateUp()
                        else onClose()
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = HighlightsRoute.RouteArticles.name,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(HighlightsRoute.RouteArticles.name) {
                    ArticlesScreen(navController, highlightViewModel) { article ->
                        highlightViewModel.launchUrl(context, article.url)
                    }
                }
                composable("${HighlightsRoute.RouteHighlights.name}/{articleId}") { entry ->
                    HighlightsScreen(
                        entry.arguments?.read { getStringOrNull("articleId") }
                            ?.toIntOrNull() ?: 0,
                        modifier = Modifier.padding(10.dp),
                        highlightViewModel,
                        deleteHighlight = { highlightViewModel.deleteHighlight(it) }
                    )
                }
            }
        }
    }
}

enum class HighlightsRoute(val titleResId: StringResource) {
    RouteArticles(Res.string.articles),
    RouteHighlights(Res.string.highlights),
}

@Composable
fun ArticlesScreen(
    navHostController: NavHostController,
    highlightViewModel: HighlightViewModel,
    onLinkClick: (Article) -> Unit,
) {
    val articles by highlightViewModel.getAllArticles().collectAsState(emptyList())
    LazyColumn(
        modifier = Modifier.padding(10.dp),
        reverseLayout = true
    ) {
        items(articles.size, key = { articles[it].id }) { index ->
            val article = articles[index]
            ArticleItem(
                modifier = Modifier.padding(vertical = 10.dp),
                navHostController = navHostController,
                article = article,
                onLinkClick = { onLinkClick(article) },
                deleteArticle = {
                    highlightViewModel.deleteArticle(article.id)
                }
            )
            if (index < articles.lastIndex) Divider(thickness = 1.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(
    modifier: Modifier,
    navHostController: NavHostController,
    article: Article,
    onLinkClick: () -> Unit = {},
    deleteArticle: (Article) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    navHostController.navigate("${HighlightsRoute.RouteHighlights.name}/${article.id}")
                },
                onLongClick = { deleteArticle(article) }
            )
    ) {
        Text(
            modifier = modifier,
            text = article.title,
            color = MaterialTheme.colors.onBackground,
        )
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onLinkClick() },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(23.dp),
                imageVector = vectorResource(Res.drawable.icon_exit),
                contentDescription = "link",
                tint = MaterialTheme.colors.onBackground,
            )
            Text(
                modifier = Modifier.padding(end = 15.dp),
                text = DateFormat.format(article.date, "MM-dd"),
                style = MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.onBackground,
                )
            )
            Icon(
                modifier = Modifier
                    .size(23.dp)
                    .clickable { deleteArticle(article) },
                imageVector = vectorResource(Res.drawable.icon_delete),
                contentDescription = "delete",
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}

@Composable
fun HighlightsScreen(
    articleId: Int,
    modifier: Modifier = Modifier,
    highlightViewModel: HighlightViewModel,
    deleteHighlight: (Highlight) -> Unit,
) {

    val highlights by highlightViewModel.getHighlightsForArticle(articleId)
        .collectAsState(emptyList())

    var articleName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val article = highlightViewModel.getArticle(articleId)
        articleName = article?.title.orEmpty()
    }

    LazyColumn(
        modifier = modifier.padding(10.dp),
    ) {
        item {
            Text(
                modifier = Modifier.padding(vertical = 10.dp),
                text = articleName,
                style = MaterialTheme.typography.h6.copy(
                    color = MaterialTheme.colors.onBackground,
                )
            )
        }
        items(highlights.size, key = { highlights[it].id }) { index ->
            HighlightItem(
                highlight = highlights[index],
                deleteHighlight = deleteHighlight,
            )
            if (index < highlights.lastIndex) Divider(thickness = 1.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighlightItem(
    highlight: Highlight,
    deleteHighlight: (Highlight) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .clickable {
                ShareUtil.copyToClipboard(context, highlight.content)
            }
    ) {
        Text(
            modifier = Modifier.padding(vertical = 10.dp),
            text = highlight.content,
            color = MaterialTheme.colors.onBackground,
        )
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        ShareUtil.copyToClipboard(context, highlight.content)
                    },
                imageVector = vectorResource(Res.drawable.ic_copy),
                contentDescription = "copy",
                tint = MaterialTheme.colors.onBackground,
            )
            Spacer(modifier = Modifier.size(10.dp))
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        deleteHighlight(highlight)
                    },
                imageVector = vectorResource(Res.drawable.icon_delete),
                contentDescription = "delete",
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}

@Composable
fun HighlightsBar(
    currentScreen: HighlightsRoute,
    onClick: () -> Unit,
    navigateUp: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(currentScreen.titleResId),
                color = MaterialTheme.colors.onPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onClick) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = vectorResource(Res.drawable.icon_export),
                    contentDescription = ""
                )
            }
        }
    )
}

@Composable
fun PreviewArticleItem() {
    MyTheme {
        ArticleItem(
            modifier = Modifier,
            article = Article(
                title = "Hello",
                url = "123",
                date = System.currentTimeMillis(),
                tags = ""
            ),
            navHostController = rememberNavController(),
            deleteArticle = {}
        )
    }
}

@Composable
fun PreviewHighlightItem() {
    MyTheme {
        HighlightItem(
            highlight = Highlight(
                articleId = 1,
                content = "Hello",
            ),
            deleteHighlight = {}
        )
    }
}
