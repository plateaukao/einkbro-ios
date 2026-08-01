package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.browser.WebViewEngineListener
import info.plateaukao.einkbro.browser.createWebViewEngine
import info.plateaukao.einkbro.data.remote.ChatMessage
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.database.DomainConfigurationData
import info.plateaukao.einkbro.epub.EpubBook
import info.plateaukao.einkbro.epub.EpubBuilder
import info.plateaukao.einkbro.epub.EpubChapter
import info.plateaukao.einkbro.epub.EpubExporter
import info.plateaukao.einkbro.epub.EpubImage
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.preference.SavedFileInfo
import info.plateaukao.einkbro.search.SearchEngine
import info.plateaukao.einkbro.search.SearchEngineUrls
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.Locale
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.sanitizeFileName
import info.plateaukao.einkbro.util.storedPathFor
import info.plateaukao.einkbro.view.Album
import info.plateaukao.einkbro.view.WebContentHelper
import info.plateaukao.einkbro.viewmodel.TtsViewModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Default [BrowserTools] implementation backed by an off-screen [WebViewEngine]
 * (a WKWebView that is never mounted in a [info.plateaukao.einkbro.browser.WebViewHost])
 * and the shared [OpenAiRepository]. One instance is created per [TaskRunner]
 * invocation and [dispose]d when the task completes or is cancelled.
 *
 * Android's BrowserToolsImpl drives an EBWebView + OkHttp; here the same surface
 * maps onto the WebViewEngine seam, Ktor, and the ported EPUB pipeline.
 */
class BrowserToolsImpl(
    private val config: ConfigManager,
    private val openAiRepository: OpenAiRepository,
    private val ttsViewModel: TtsViewModel,
    private val progressSink: (TaskProgress.StepLine) -> Unit,
    private val finishSink: (String) -> Unit,
    private val initialSnapshot: InitialPageSnapshot? = null,
    /** Resolves the user's active tab engine at call time (null = no tab open). */
    private val activeEngineProvider: () -> WebViewEngine? = { null },
) : BrowserTools {

    private var bgEngine: WebViewEngine? = null
    private var bgHelper: WebContentHelper? = null
    private var currentLoadDeferred: CompletableDeferred<Boolean>? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val bgListener = object : WebViewEngineListener {
        override fun onPageFinished(engine: WebViewEngine, url: String, title: String) {
            currentLoadDeferred?.complete(true)
        }
    }

    // ── JavaScript evaluation ───────────────────────────────────────────

    override suspend fun runJavascriptInBg(code: String): String? {
        val engine = bgEngine ?: return null
        return evaluateJs(engine, code)
    }

    override suspend fun runJavascriptInInitialTab(code: String): String? {
        val engine = initialSnapshot?.originEngineProvider?.invoke() ?: return null
        return evaluateJs(engine, code)
    }

    /** Stringified completion value; WKWebView returns plain text for JS strings
     *  (Android's evaluateJavascript JSON-encodes — scripts should JSON.stringify
     *  structured results, which yields the same JSON text on both platforms). */
    private suspend fun evaluateJs(engine: WebViewEngine, code: String): String? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(JS_EVAL_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    try {
                        engine.evaluateJavascript(code) { result ->
                            if (cont.isActive) cont.resume(result ?: "null")
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume("error: ${e.message}")
                    }
                }
            } ?: "error: javascript evaluation timed out"
        }

    // ── Navigation / content ────────────────────────────────────────────

    override suspend fun openUrlInBg(url: String): Boolean {
        val engine = ensureBgEngine()
        val deferred = CompletableDeferred<Boolean>()
        currentLoadDeferred = deferred
        withContext(Dispatchers.Main) { engine.loadUrl(url) }
        val ok = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) { deferred.await() } ?: false
        currentLoadDeferred = null
        return ok
    }

    override suspend fun searchInBg(query: String): Boolean {
        val ordinal = config.browser.searchEngine.toIntOrNull() ?: SearchEngine.GOOGLE.ordinal
        val searchUrl = SearchEngineUrls.searchUrl(
            ordinal = ordinal,
            encodedQuery = SearchEngineUrls.percentEncodeQuery(query),
            customTemplate = config.browser.searchEngineUrl
                .ifBlank { "https://www.google.com/search?q=%s" },
        )
        return openUrlInBg(searchUrl)
    }

    override suspend fun currentBgPageText(): String {
        val helper = bgHelper ?: return ""
        return rawTextOf(helper)
    }

    override suspend fun currentBgPageLinks(): List<BrowserTools.Link> {
        val engine = bgEngine ?: return emptyList()
        return parseLinks(evaluateJs(engine, LINKS_JS).orEmpty())
    }

    override suspend fun activeTabText(): String {
        val engine = activeEngineProvider() ?: return ""
        return rawTextOf(WebContentHelper(engine))
    }

    override suspend fun activeTabLinks(): List<BrowserTools.Link> {
        val engine = activeEngineProvider() ?: return emptyList()
        return parseLinks(evaluateJs(engine, LINKS_JS).orEmpty())
    }

    override fun activeTabUrl(): String = activeEngineProvider()?.currentUrl().orEmpty()

    override fun activeTabTitle(): String = activeEngineProvider()?.album?.albumTitle.orEmpty()

    /** get_raw_text.js through the ported reader helper, bounded like Android's
     *  READER_EXTRACT_TIMEOUT_MS (the JS callback is not guaranteed to fire). */
    private suspend fun rawTextOf(helper: WebContentHelper): String =
        withContext(Dispatchers.Main) {
            // On a YouTube watch page this swaps in the caption transcript. It
            // sits outside the timeout on purpose: a Gemini transcription runs
            // for minutes, while READER_EXTRACT_TIMEOUT_MS only guards the JS
            // callback that may never fire.
            helper.prepareVideoTranscript()
            withTimeoutOrNull(READER_EXTRACT_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    helper.getRawText { text ->
                        if (cont.isActive) cont.resume(text)
                    }
                }
            }
        } ?: ""

    // ── Originating page snapshot ──────────────────────────────────────

    override fun initialPageUrl(): String = initialSnapshot?.url.orEmpty()
    override fun initialPageTitle(): String = initialSnapshot?.title.orEmpty()
    override fun initialPageText(): String = initialSnapshot?.text.orEmpty()
    override fun initialPageLinks(): List<BrowserTools.Link> = initialSnapshot?.links.orEmpty()
    override fun initialPageRawHtml(): String = initialSnapshot?.rawHtml.orEmpty()

    // ── Page source (network-level) ─────────────────────────────────────

    /** Last fetched (url, html) pair so paged tool reads don't re-download. */
    private var pageSourceCache: Pair<String, String>? = null

    override suspend fun fetchPageSource(url: String): String? {
        pageSourceCache?.let { if (it.first == url) return it.second }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            val response = HttpClientProvider.client.get(url) {
                header("User-Agent", MOBILE_USER_AGENT)
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            if (response.status.value !in 200..299) return null
            val contentType = response.headers["Content-Type"].orEmpty().lowercase()
            if (BINARY_TYPE_PREFIXES.any { contentType.startsWith(it) }) return null
            val text = response.bodyAsText()
            val capped = if (text.length > MAX_SOURCE_CHARS) text.take(MAX_SOURCE_CHARS) else text
            pageSourceCache = url to capped
            capped
        } catch (e: Exception) {
            null
        }
    }

    // ── Domain config ───────────────────────────────────────────────────

    private fun initialHost(): String? {
        val url = initialSnapshot?.url ?: return null
        return Uri.parse(url).host?.takeIf { it.isNotBlank() }
    }

    override fun getInitialDomainJavascript(): String {
        val host = initialHost() ?: return ""
        return config.domainConfigurationMap[host]?.postLoadJavascript.orEmpty()
    }

    override fun getInitialDomainCss(): String {
        val host = initialHost() ?: return ""
        return config.domainConfigurationMap[host]?.customCss.orEmpty()
    }

    override fun setInitialDomainJavascript(code: String) {
        val host = initialHost() ?: return
        val current = config.domainConfigurationMap[host] ?: DomainConfigurationData(host)
        config.updateDomainConfig(current.copy(postLoadJavascript = code.ifBlank { null }))
    }

    override fun setInitialDomainCss(code: String) {
        val host = initialHost() ?: return
        val current = config.domainConfigurationMap[host] ?: DomainConfigurationData(host)
        config.updateDomainConfig(current.copy(customCss = code.ifBlank { null }))
    }

    private fun parseLinks(raw: String): List<BrowserTools.Link> {
        if (raw.isBlank() || raw == "null") return emptyList()
        return try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(RawLink.serializer()), raw
            ).map { BrowserTools.Link(it.text, it.href) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class RawLink(val text: String, val href: String)

    // ── LLM ─────────────────────────────────────────────────────────────

    override suspend fun askLlm(system: String, user: String): String? {
        val action = summarizeActionInfo()
        val messages = mutableListOf<ChatMessage>()
        if (system.isNotBlank()) messages += ChatMessage(content = system, role = ChatRole.System)
        messages += ChatMessage(content = user, role = ChatRole.User)

        return withContext(Dispatchers.Default) {
            if (action.actionType == GptActionType.Gemini) {
                openAiRepository.queryGemini(messages, action).valueOrNull()?.takeIf { it.isNotBlank() }
            } else {
                val completion = openAiRepository.chatCompletion(messages, action)
                completion?.choices
                    ?.firstOrNull { it.message.role == ChatRole.Assistant }
                    ?.message?.content
            }
        }
    }

    override fun summarizeActionInfo(): ChatGPTActionInfo = ChatGPTActionInfo(
        name = "task",
        systemMessage = "You are a helpful assistant that follows instructions precisely.",
        userMessage = "",
        actionType = config.ai.getDefaultActionType(),
        model = config.ai.getDefaultActionModel(),
    )

    override fun defaultLanguageName(): String {
        val tag = config.uiLocaleLanguage.ifBlank { null }
        val locale = if (tag != null) {
            Locale(tag.substringBefore('-').substringBefore('_'))
        } else {
            Locale.getDefault()
        }
        val name = locale.getDisplayName(Locale.ENGLISH)
        return if (name.isBlank()) "the user's language" else name
    }

    override fun speak(text: String, title: String) {
        if (text.isBlank()) return
        ttsViewModel.readArticle(text, title)
    }

    // ── EPUB export ─────────────────────────────────────────────────────

    private val epubExporter by lazy { EpubExporter() }

    override suspend fun saveEpub(
        bookName: String,
        chapters: List<BrowserTools.EpubChapterSpec>,
        onChapterLoaded: (Int, Int, String) -> Unit,
    ): String? {
        if (chapters.isEmpty()) return null

        // EpubExporter.export writes + shares one chapter at a time; a multi-chapter
        // book is assembled here instead (its capture parser and the shared
        // EpubBuilder do the heavy lifting), then written once.
        val built = mutableListOf<EpubChapter>()
        chapters.forEachIndexed { index, spec ->
            if (!openUrlInBg(spec.url)) return@forEachIndexed
            val helper = bgHelper ?: return@forEachIndexed
            val raw = withContext(Dispatchers.Main) {
                withTimeoutOrNull(READER_EXTRACT_TIMEOUT_MS) {
                    suspendCancellableCoroutine<String> { cont ->
                        helper.getEpubChapter { capture ->
                            if (cont.isActive) cont.resume(capture)
                        }
                    }
                }
            } ?: return@forEachIndexed
            val capture = epubExporter.parseCapture(raw) ?: return@forEachIndexed
            if (capture.error != null || capture.xhtml.isBlank()) return@forEachIndexed

            val title = spec.title.ifBlank { capture.title.ifBlank { spec.url } }
            // Namespace this chapter's images so chapters that both start numbering
            // at 0 don't collide (same scheme as EpubExporter.export).
            val prefix = "images/c${built.size}_"
            var body = capture.xhtml.replace("\"images/", "\"$prefix")
            val images = mutableListOf<EpubImage>()
            capture.images.forEach { img ->
                val newName = img.name.replace("images/", prefix)
                val bytes = try {
                    HttpClientProvider.client.get(img.url).body<ByteArray>()
                } catch (e: Exception) {
                    null
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    val ext = newName.substringAfterLast('.', "jpg")
                    images.add(EpubImage(newName, EpubBuilder.mediaTypeForExt(ext), bytes))
                } else {
                    // Drop a failed image so the EPUB has no dangling reference.
                    body = body.replace("src=\"$newName\"", "src=\"\"")
                }
            }
            built += EpubChapter(title, body, images)
            onChapterLoaded(index + 1, chapters.size, title)
        }
        if (built.isEmpty()) return null

        val book = EpubBook(
            title = bookName.ifBlank { "EinkBro book" },
            author = Uri.parse(chapters.first().url).host ?: "EinkBro",
            identifier = "urn:uuid:einkbro-task-" +
                (bookName + chapters.first().url).hashCode().toUInt().toString(16),
            chapters = built,
        )
        val bytes = EpubBuilder.build(book)
        val path = FileStore.writeBytes(
            "epub", "${sanitizeFileName(book.title, "book")}.epub", bytes
        ) ?: return null

        // Persist the container-relative form; absolute paths die on reinstall.
        val storedPath = storedPathFor(path)
        if (config.savedEpubFileInfos.none { it.uri == storedPath }) {
            config.addSavedEpubFile(SavedFileInfo(book.title, storedPath))
        }
        return path
    }

    // ── Progress reporting ──────────────────────────────────────────────

    override fun info(text: String) =
        progressSink(TaskProgress.StepLine(TaskProgress.StepLine.Kind.Info, text))

    override fun tool(text: String) =
        progressSink(TaskProgress.StepLine(TaskProgress.StepLine.Kind.Tool, text))

    override fun error(text: String) =
        progressSink(TaskProgress.StepLine(TaskProgress.StepLine.Kind.Error, text))

    override fun finish(markdown: String) = finishSink(markdown)

    // ── Lifecycle ───────────────────────────────────────────────────────

    /** WKWebView creation must happen on the main thread; the engine is created
     *  detached and never mounted, so it stays off-screen for its whole life. */
    private suspend fun ensureBgEngine(): WebViewEngine = withContext(Dispatchers.Main) {
        bgEngine ?: createWebViewEngine(Album(), bgListener, incognito = false).also {
            bgEngine = it
            bgHelper = WebContentHelper(it)
        }
    }

    override fun dispose() {
        val engine = bgEngine ?: return
        try {
            engine.stopLoading()
            engine.destroy()
        } catch (_: Exception) {
        }
        bgEngine = null
        bgHelper = null
        currentLoadDeferred?.complete(false)
        currentLoadDeferred = null
    }

    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
        private const val JS_EVAL_TIMEOUT_MS = 10_000L

        // Reader-mode extraction goes through injected JS whose callback is not
        // guaranteed to fire on every page — never wait on it unbounded.
        private const val READER_EXTRACT_TIMEOUT_MS = 15_000L

        // Memory guard for fetched page sources; windowed tool reads never need more.
        private const val MAX_SOURCE_CHARS = 2_000_000

        // Content types that would decode to garbage if treated as page source.
        private val BINARY_TYPE_PREFIXES = listOf(
            "image/", "video/", "audio/", "font/",
            "application/octet-stream", "application/pdf", "application/zip",
        )

        // Mobile Safari UA for network-level source fetches (Android asked the
        // WebView; the WKWebView UA is not reachable synchronously from common).
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

        /** Anchor extraction: JSON.stringify keeps the wire format identical to
         *  Android's jsBridge.getPageLinks() (a JSON array of {text, href}). */
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
}
