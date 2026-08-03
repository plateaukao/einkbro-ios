package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.ApiResult
import info.plateaukao.einkbro.data.remote.ChatMessage
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.toSystemMessage
import info.plateaukao.einkbro.data.remote.toUserMessage
import info.plateaukao.einkbro.database.ChatSession
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.task.AgentSession
import info.plateaukao.einkbro.task.BrowserToolsImpl
import info.plateaukao.einkbro.task.InitialPageSnapshot
import info.plateaukao.einkbro.task.TaskProgress
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.viewmodel.TtsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Port of Android's browser/ChatWebInterface.kt: the native side of the shared
 * chat.html page. Android exposes this object synchronously via
 * @JavascriptInterface; WKWebView has no synchronous bridge, so an injected
 * user script ([BRIDGE_SHIM_JS]) emulates `window.AndroidInterface` over a
 * webkit message handler — fire-and-forget methods post straight through, and
 * the two value-returning methods (getWebMetadata / loadChatSessions) return
 * Promises resolved by [resolve]. chat.html awaits those two calls, which is a
 * no-op await on Android where the real interface returns strings.
 *
 * Agent mode routes each user message through [AgentSession]'s tool-calling
 * loop instead of chatStream; tool lines and answers stream into the page the
 * same way Android's inline agent loop does (via receiveMessageFromAndroid).
 */
class ChatWebInterface(
    private val scope: CoroutineScope,
    private val engine: WebViewEngine,
    private var webContent: String,
    private var webTitle: String,
    private var webUrl: String,
    onOpenNewTab: ((String) -> Unit)? = null,
    private val agentMode: Boolean = false,
    agentSnapshot: InitialPageSnapshot? = null,
    agentTtsViewModel: TtsViewModel? = null,
    agentActiveEngineProvider: (() -> WebViewEngine?)? = null,
) {
    private val openAiRepository = OpenAiRepository()
    private val config = AppServices.config
    private val bookmarkManager = AppServices.bookmarkManager

    // Plain mode: API-facing history (role/content pairs), separate from the
    // page-rendered bubbles the page persists itself.
    private val chatHistory: MutableList<ChatMessage> = mutableListOf()

    private val jsHelper = JsHelper(engine, scope, onOpenNewTab)

    private val json = Json { ignoreUnknownKeys = true }

    // Agent mode: the tool-calling loop plus its off-screen browser tools
    // (Android builds the same loop inline; iOS reuses AgentSession).
    private var agent: AgentSession? = null

    // Fired once the page signals readiness (its first getWebMetadata call):
    // the preset GPT-action prompt or the custom-task prompt, injected through
    // the page's own sendMessageToAndroid so the user bubble renders too.
    private var pendingInitialPrompt: String? = null

    init {
        engine.installUserScript(BRIDGE_SHIM_JS, atDocumentStart = true)
        engine.addMessageHandler(HANDLER_NAME) { payload -> handleBridgeMessage(payload) }
        if (agentMode) {
            val tools = BrowserToolsImpl(
                config = config,
                openAiRepository = openAiRepository,
                ttsViewModel = requireNotNull(agentTtsViewModel) {
                    "agent-mode deps not wired; check the chat-tab call site"
                },
                progressSink = { line -> jsHelper.sendStreamUpdate("\n\n" + formatStep(line)) },
                finishSink = { markdown ->
                    if (markdown.isNotBlank()) jsHelper.sendStreamUpdate("\n\n$markdown")
                },
                initialSnapshot = agentSnapshot,
                activeEngineProvider = agentActiveEngineProvider ?: { null },
            )
            agent = AgentSession(tools)
        }
    }

    /** Materializes chat.html (+ marked.min.js beside it, so its relative
     *  script URL resolves) and loads it — Android's loadUrl(chat.html). */
    fun loadChatPage(initialPrompt: String? = null) {
        pendingInitialPrompt = initialPrompt?.takeIf { it.isNotBlank() }
        val page = FileStore.writeBytes(
            CHAT_DIR, "chat.html", Assets.get("chat.html").encodeToByteArray()
        )
        FileStore.writeBytes(
            CHAT_DIR, "marked.min.js", Assets.get("marked.min.js").encodeToByteArray()
        )
        if (page != null) engine.loadFile(page)
    }

    /** Cancels any in-flight turn and releases the agent's off-screen engine.
     *  The tab's own web view is destroyed by closeTab, not here. */
    fun dispose() {
        openAiRepository.cancel()
        agent?.dispose()
        agent = null
    }

    fun updateWebContent(
        newWebContent: String,
        newWebTitle: String = webTitle,
        newWebUrl: String = webUrl,
    ) {
        this.webContent = newWebContent
        this.webTitle = newWebTitle
        this.webUrl = newWebUrl
    }

    // ── Bridge dispatch ────────────────────────────────────────────────
    // The shim posts {"method", "arg", "callbackId"}; methods with a callbackId
    // expect a resolve() back into the page.

    private fun handleBridgeMessage(payload: String) {
        val msg = runCatching { json.decodeFromString<BridgeMessage>(payload) }.getOrNull()
            ?: return
        when (msg.method) {
            "sendMessage" -> sendMessage(msg.arg.orEmpty())
            "getWebMetadata" -> {
                resolve(msg.callbackId, getWebMetadata())
                onPageReady()
            }
            "openUrlInNewTab" -> msg.arg?.let { jsHelper.openUrlInNewTab(it) }
            "loadChatSessions" -> scope.launch { resolve(msg.callbackId, loadChatSessions()) }
            "saveChatSession" -> msg.arg?.let { saveChatSession(it) }
            "deleteChatSession" -> msg.arg?.let { deleteChatSession(it) }
            "deleteAllChatSessions" -> deleteAllChatSessions()
            "restoreChatSession" -> msg.arg?.let { restoreChatSession(it) }
        }
    }

    /** Resolves a shim Promise: the page's __einkbroBridgeResolve looks up the
     *  pending callback and hands it [value] (always a string, like Android's
     *  synchronous returns). */
    private fun resolve(callbackId: Int?, value: String) {
        if (callbackId == null) return
        engine.evaluateJavascript(
            "window.__einkbroBridgeResolve($callbackId, '${escapeJsString(value)}')"
        )
    }

    /** The page is initialized (it asked for the web metadata) — fire the
     *  queued preset/agent prompt through the page so its user bubble shows. */
    private fun onPageReady() {
        val prompt = pendingInitialPrompt ?: return
        pendingInitialPrompt = null
        jsHelper.sendUserMessage(prompt)
    }

    // ── Bridge methods (Android's @JavascriptInterface surface) ────────

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        val chatGptActionInfo = createChatGptActionInfo(message)
        scope.launch(Dispatchers.Main) {
            sendMessageWithGptActionInfo(chatGptActionInfo)
        }
    }

    fun getWebMetadata(): String =
        """{"title": "${escapeJsonString(webTitle)}", "url": "${escapeJsonString(webUrl)}"}"""

    // ── Chat session persistence ───────────────────────────────────────
    // The chat page keeps sessions in the app database (not page localStorage)
    // so they survive WebView storage clearing and are included in backups.
    // The map shape returned here matches the page's in-memory `sessions` object.

    private suspend fun loadChatSessions(): String = try {
        buildJsonObject {
            bookmarkManager.getAllChatSessions().forEach { session ->
                put(session.id, buildJsonObject {
                    put("id", session.id)
                    put("title", session.title)
                    put("created", session.created)
                    put("lastUpdated", session.lastUpdated)
                    put("webTitle", session.webTitle)
                    put("webUrl", session.webUrl)
                    put(
                        "messages",
                        runCatching { json.parseToJsonElement(session.messages) }
                            .getOrElse { JsonArray(emptyList()) },
                    )
                })
            }
        }.toString()
    } catch (e: Exception) {
        "{}"
    }

    fun saveChatSession(sessionJson: String) {
        // Agent task transcripts (tool-call bubbles, progress lines) are working
        // output, not conversations to revisit — only real chat-with-web sessions
        // belong in the persisted history.
        if (agentMode) return
        scope.launch {
            try {
                val obj = json.parseToJsonElement(sessionJson).jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@launch
                // The page never sends webContent (it can be hundreds of KB and
                // never changes after creation): an existing row keeps its
                // stored copy; a new row is seeded from this tab's capture.
                val storedWebContent = bookmarkManager.getChatSessionById(id)?.webContent
                bookmarkManager.upsertChatSession(
                    ChatSession(
                        id = id,
                        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        created = obj["created"]?.jsonPrimitive?.longOrNull ?: 0L,
                        lastUpdated = obj["lastUpdated"]?.jsonPrimitive?.longOrNull ?: 0L,
                        webTitle = obj["webTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        webUrl = obj["webUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        messages = (obj["messages"] as? JsonArray)?.toString() ?: "[]",
                        webContent = storedWebContent ?: webContent,
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * The page switched to a stored session (chat.html loadSession): rebuild
     * the LLM-facing state from the row so follow-ups continue the restored
     * conversation — its history AND its page text, not the tab's. Freshly
     * created sessions have no row yet and no-op.
     */
    fun restoreChatSession(sessionId: String) {
        if (agentMode) return
        scope.launch {
            val session = runCatching { bookmarkManager.getChatSessionById(sessionId) }
                .getOrNull() ?: return@launch
            webTitle = session.webTitle
            webUrl = session.webUrl
            webContent = session.webContent
            chatHistory.clear()
            runCatching { json.parseToJsonElement(session.messages).jsonArray }.getOrNull()
                ?.forEach { element ->
                    val message = element as? JsonObject ?: return@forEach
                    val content = message["content"]?.jsonPrimitive?.contentOrNull
                        ?: return@forEach
                    val isUser = message["isUser"]?.jsonPrimitive?.booleanOrNull ?: false
                    chatHistory.add(
                        ChatMessage(content, if (isUser) ChatRole.User else ChatRole.Assistant)
                    )
                }
        }
    }

    fun deleteChatSession(sessionId: String) {
        scope.launch {
            runCatching { bookmarkManager.deleteChatSession(sessionId) }
        }
    }

    fun deleteAllChatSessions() {
        scope.launch {
            runCatching { bookmarkManager.deleteAllChatSessions() }
        }
    }

    // ── Chat turns ─────────────────────────────────────────────────────

    fun sendMessageWithGptActionInfo(gptActionInfo: ChatGPTActionInfo) {
        if (agentMode) {
            runAgentTurn(gptActionInfo.userMessage)
            return
        }

        val currentUserMessage = gptActionInfo.userMessage.toUserMessage()

        val messagesForApi = mutableListOf<ChatMessage>().apply {
            if (gptActionInfo.systemMessage.isNotEmpty()) {
                add(gptActionInfo.systemMessage.toSystemMessage())
            }
            // Blank after restoring a session saved before webContent was
            // persisted — send no page context rather than an empty code block.
            if (webContent.isNotBlank()) add(createWebContentMessage(webContent))
            addAll(chatHistory)
            add(currentUserMessage)
        }

        val assistantResponseAggregator = StringBuilder()

        jsHelper.startMessageStream {
            openAiRepository.chatStream(
                messages = messagesForApi,
                gptActionInfo = gptActionInfo,
                appendResponseAction = { responseChunk ->
                    jsHelper.sendStreamUpdate(responseChunk)
                    assistantResponseAggregator.append(responseChunk)
                },
                doneAction = {
                    jsHelper.sendFinalEmptyUpdate()
                    chatHistory.add(currentUserMessage)
                    chatHistory.add(
                        ChatMessage(
                            content = assistantResponseAggregator.toString(),
                            role = ChatRole.Assistant,
                        )
                    )
                },
                failureAction = { failure ->
                    val userMessage = when (failure.kind) {
                        ApiResult.Kind.MissingKey -> failure.message
                        ApiResult.Kind.RateLimited -> failure.retryAfterSeconds
                            ?.let { "Rate limited — retry after ${it}s" }
                            ?: "Rate limited — try again shortly"
                        ApiResult.Kind.Network -> "Network error — check connection"
                        else -> "AI request failed: ${failure.message}"
                    }
                    jsHelper.sendErrorUpdate(userMessage)
                },
            )
        }
    }

    // ── Agent mode ─────────────────────────────────────────────────────

    private fun runAgentTurn(userMessage: String) {
        val runningAgent = agent ?: return
        jsHelper.startMessageStream {
            scope.launch {
                try {
                    runningAgent.runTurn(userMessage)
                } finally {
                    jsHelper.sendFinalEmptyUpdate()
                }
            }
        }
    }

    private fun formatStep(line: TaskProgress.StepLine): String = when (line.kind) {
        // Code-span the tool line so underscores in tool names/args don't
        // read as markdown emphasis (Android backticks the name the same way).
        TaskProgress.StepLine.Kind.Tool -> "🔧 `${line.text}`"
        TaskProgress.StepLine.Kind.Error -> "⚠️ ${line.text}"
        TaskProgress.StepLine.Kind.Info -> line.text
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun createWebContentMessage(content: String): ChatMessage =
        "```$content```$WEB_CONTENT_MESSAGE_SUFFIX".toUserMessage()

    private fun createChatGptActionInfo(message: String): ChatGPTActionInfo {
        val type = config.ai.gptForChatWeb
        val concreteType =
            if (type == GptActionType.Default) config.ai.getDefaultActionType() else type
        return ChatGPTActionInfo(
            name = "chat",
            actionType = concreteType,
            model = config.ai.getGptTypeModelMap()[type] ?: config.ai.gptModel,
            userMessage = message,
        )
    }

    private fun escapeJsonString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    @Serializable
    private data class BridgeMessage(
        val method: String = "",
        val arg: String? = null,
        val callbackId: Int? = null,
    )

    companion object {
        private const val WEB_CONTENT_MESSAGE_SUFFIX = "\n this is the web content;"
        private const val HANDLER_NAME = "einkbroChat"
        private const val CHAT_DIR = "chat"

        // Emulates Android's synchronous window.AndroidInterface: fire-and-forget
        // methods post through the message handler; the two value-returning ones
        // hand back a Promise that resolve() completes. onContentHeightChanged is
        // deliberately absent — chat.html guards every call with a typeof check.
        private val BRIDGE_SHIM_JS = """
            (function() {
              if (window.AndroidInterface) return;
              var nextCallbackId = 1;
              var pending = {};
              window.__einkbroBridgeResolve = function(id, value) {
                var cb = pending[id];
                if (cb) { delete pending[id]; cb(value); }
              };
              function post(method, arg) {
                window.webkit.messageHandlers.$HANDLER_NAME.postMessage(
                  JSON.stringify({ method: method, arg: arg == null ? null : String(arg) }));
              }
              function call(method) {
                return new Promise(function(resolve) {
                  var id = nextCallbackId++;
                  pending[id] = resolve;
                  window.webkit.messageHandlers.$HANDLER_NAME.postMessage(
                    JSON.stringify({ method: method, callbackId: id }));
                });
              }
              window.AndroidInterface = {
                sendMessage: function(m) { post('sendMessage', m); },
                getWebMetadata: function() { return call('getWebMetadata'); },
                openUrlInNewTab: function(u) { post('openUrlInNewTab', u); },
                loadChatSessions: function() { return call('loadChatSessions'); },
                saveChatSession: function(j) { post('saveChatSession', j); },
                deleteChatSession: function(id) { post('deleteChatSession', id); },
                deleteAllChatSessions: function() { post('deleteAllChatSessions'); },
                restoreChatSession: function(id) { post('restoreChatSession', id); },
              };
            })();
        """.trimIndent()
    }
}

/**
 * Helper class to manage JavaScript interactions with the chat page.
 * Stateless regarding message content, only forwarding to JS (Android's
 * JsHelper, over the engine seam instead of a WebView).
 */
class JsHelper(
    private val engine: WebViewEngine,
    private val scope: CoroutineScope,
    private val onOpenNewTab: ((String) -> Unit)? = null,
) {
    fun startMessageStream(postAction: () -> Unit = {}) {
        engine.evaluateJavascript("startMessageStream()") {
            postAction()
        }
    }

    fun sendStreamUpdate(messageChunk: String) {
        scope.launch(Dispatchers.Main) {
            engine.evaluateJavascript(
                "receiveMessageFromAndroid('${escapeJsString(messageChunk)}', true, false)"
            )
        }
    }

    fun sendFinalEmptyUpdate() {
        scope.launch(Dispatchers.Main) {
            engine.evaluateJavascript("receiveMessageFromAndroid('', true, true)")
        }
    }

    fun sendErrorUpdate(errorMessage: String) {
        scope.launch(Dispatchers.Main) {
            engine.evaluateJavascript(
                "receiveMessageFromAndroid('${escapeJsString(errorMessage)}', true, true)"
            )
        }
    }

    /** Types [message] into the page's own send path (user bubble + history +
     *  typing indicator), used for preset-action and agent initial prompts. */
    fun sendUserMessage(message: String) {
        scope.launch(Dispatchers.Main) {
            engine.evaluateJavascript("sendMessageToAndroid('${escapeJsString(message)}')")
        }
    }

    fun openUrlInNewTab(url: String) {
        scope.launch(Dispatchers.Main) {
            onOpenNewTab?.invoke(url)
        }
    }
}

internal fun escapeJsString(str: String): String {
    return str.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\"", "\\\"")
}
