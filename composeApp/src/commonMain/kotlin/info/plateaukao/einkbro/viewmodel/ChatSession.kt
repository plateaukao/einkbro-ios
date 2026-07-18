package info.plateaukao.einkbro.viewmodel

import androidx.compose.ui.text.AnnotatedString
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.data.remote.ApiResult
import info.plateaukao.einkbro.data.remote.ChatMessage
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.toUserMessage
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.task.AgentSession
import info.plateaukao.einkbro.task.BrowserToolsImpl
import info.plateaukao.einkbro.task.InitialPageSnapshot
import info.plateaukao.einkbro.task.TaskProgress
import info.plateaukao.einkbro.unit.HelperUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One native chat tab's conversation (parity Phase K / Android's chat.html tab).
 * Android loads `chat.html` into a new tab and drives it through
 * ChatWebInterface; iOS renders a native Compose chat tab over this session.
 *
 * Two modes, mirroring ChatWebInterface's agentMode flag:
 * - plain ([agentMode] = false): the page's raw text is injected once as
 *   context, then each user turn streams an assistant reply from the
 *   gptForChatWeb-configured engine.
 * - agent ([agentMode] = true): each user turn runs the [AgentSession]
 *   tool-calling loop; tool calls, notes, and the final answer accumulate in
 *   that turn's assistant bubble (Android streams them into one bubble too).
 *
 * The session lives exactly as long as its tab: BrowserViewModel disposes it in
 * closeTab.
 */
class ChatSession(
    val agentMode: Boolean = false,
    private val config: ConfigManager = AppServices.config,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val openAiRepository = OpenAiRepository()

    var webContent: String = ""
        private set
    var webTitle: String = ""
        private set
    var webUrl: String = ""
        private set

    data class ChatUiMessage(
        val role: ChatRole,
        val text: String,
        val rendered: AnnotatedString,
    )

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    // Plain mode: API-facing history (user/assistant), separate from the UI bubbles.
    private val chatHistory = mutableListOf<ChatMessage>()

    // Agent mode: the tool-calling loop plus its off-screen browser tools.
    private var agent: AgentSession? = null
    private var turnJob: Job? = null

    fun hasApiKey(): Boolean =
        config.ai.gptApiKey.isNotBlank() || config.ai.geminiApiKey.isNotBlank() ||
            config.ai.useCustomGptUrl

    /** Plain mode: start a fresh conversation with the given page as context. */
    fun startChat(content: String, title: String, url: String) {
        webContent = content
        webTitle = title
        webUrl = url
        chatHistory.clear()
        _messages.value = emptyList()
        _inProgress.value = false
    }

    /**
     * Agent mode: wire up [BrowserToolsImpl] over the originating-page
     * [snapshot] and fire [initialPrompt] as the first turn (the custom-task
     * prompt). The snapshot may be null when no page was open — the agent then
     * reports the page as unavailable, same as the task runner did.
     */
    fun startAgent(
        ttsViewModel: TtsViewModel,
        snapshot: InitialPageSnapshot?,
        activeEngineProvider: () -> WebViewEngine?,
        initialPrompt: String?,
    ) {
        webTitle = snapshot?.title.orEmpty()
        webUrl = snapshot?.url.orEmpty()
        val tools = BrowserToolsImpl(
            config = config,
            openAiRepository = openAiRepository,
            ttsViewModel = ttsViewModel,
            progressSink = { line -> appendToAssistant(formatStep(line)) },
            finishSink = { markdown ->
                if (markdown.isNotBlank()) appendToAssistant(markdown)
            },
            initialSnapshot = snapshot,
            activeEngineProvider = activeEngineProvider,
        )
        agent = AgentSession(tools)
        initialPrompt?.takeIf { it.isNotBlank() }?.let { send(it) }
    }

    fun cancel() {
        turnJob?.cancel()
        turnJob = null
        openAiRepository.cancel()
        markStoppedIfBlank()
        _inProgress.value = false
    }

    /** Tears the session down with its tab (also releases the agent's off-screen engine). */
    fun dispose() {
        cancel()
        agent?.dispose()
        agent = null
        scope.cancel()
    }

    /** Optionally fire an initial GPT action (used when opened from a page-AI action). */
    fun runInitialAction(action: ChatGPTActionInfo) {
        val prompt = action.userMessage.ifBlank { action.systemMessage }
        if (prompt.isNotBlank()) send(prompt)
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _inProgress.value) return

        // Append the user bubble and an empty assistant bubble to stream into.
        _messages.value = _messages.value +
            ChatUiMessage(ChatRole.User, trimmed, AnnotatedString(trimmed)) +
            ChatUiMessage(ChatRole.Assistant, "", AnnotatedString(""))
        _inProgress.value = true

        if (agentMode) {
            val runningAgent = agent
            if (runningAgent == null) {
                _inProgress.value = false
                return
            }
            turnJob = scope.launch {
                try {
                    runningAgent.runTurn(trimmed)
                } finally {
                    markStoppedIfBlank()
                    _inProgress.value = false
                }
            }
            return
        }

        val actionInfo = resolveActionInfo(trimmed)
        val userMsg = trimmed.toUserMessage()

        val apiMessages = mutableListOf<ChatMessage>().apply {
            add(createWebContentMessage(webContent))
            addAll(chatHistory)
            add(userMsg)
        }

        val aggregate = StringBuilder()

        if (actionInfo.actionType == GptActionType.Gemini) {
            scope.launch {
                when (val result = openAiRepository.queryGemini(apiMessages, actionInfo)) {
                    is ApiResult.Success -> {
                        aggregate.append(result.value)
                        updateAssistant(aggregate.toString())
                        finishTurn(userMsg, aggregate.toString())
                    }

                    is ApiResult.Failure -> failTurn(result)
                }
            }
            return
        }

        openAiRepository.chatStream(
            messages = apiMessages,
            gptActionInfo = actionInfo,
            appendResponseAction = { chunk ->
                aggregate.append(chunk)
                updateAssistant(aggregate.toString())
            },
            doneAction = { finishTurn(userMsg, aggregate.toString()) },
            failureAction = { failure -> failTurn(failure) },
        )
    }

    private fun resolveActionInfo(userMessage: String): ChatGPTActionInfo {
        val type = config.ai.gptForChatWeb
        val concreteType =
            if (type == GptActionType.Default) config.ai.getDefaultActionType() else type
        val model = config.ai.getGptTypeModelMap()[type] ?: config.ai.gptModel
        return ChatGPTActionInfo(
            name = "chat",
            actionType = concreteType,
            model = model,
            userMessage = userMessage,
        )
    }

    private fun createWebContentMessage(content: String): ChatMessage =
        "```$content```\n this is the web content;".toUserMessage()

    private fun formatStep(line: TaskProgress.StepLine): String = when (line.kind) {
        // Code-span the tool line so underscores in tool names/args don't
        // read as markdown emphasis (Android backticks the name the same way).
        TaskProgress.StepLine.Kind.Tool -> "🔧 `${line.text}`"
        TaskProgress.StepLine.Kind.Error -> "⚠️ ${line.text}"
        TaskProgress.StepLine.Kind.Info -> line.text
    }

    private fun updateAssistant(text: String) {
        val list = _messages.value.toMutableList()
        val idx = list.indexOfLast { it.role == ChatRole.Assistant }
        if (idx >= 0) {
            list[idx] = list[idx].copy(text = text, rendered = HelperUnit.parseMarkdown(text))
            _messages.value = list
        }
    }

    /** Agent mode: append a segment (tool line, note, or answer) to the turn's bubble. */
    private fun appendToAssistant(segment: String) {
        val current = _messages.value.lastOrNull { it.role == ChatRole.Assistant }?.text.orEmpty()
        updateAssistant(if (current.isBlank()) segment else "$current\n\n$segment")
    }

    /** A cancelled/failed turn must not leave an empty bubble behind. */
    private fun markStoppedIfBlank() {
        val last = _messages.value.lastOrNull { it.role == ChatRole.Assistant } ?: return
        if (last.text.isBlank()) updateAssistant("(stopped)")
    }

    private fun finishTurn(userMsg: ChatMessage, assistantText: String) {
        chatHistory.add(userMsg)
        chatHistory.add(ChatMessage(content = assistantText, role = ChatRole.Assistant))
        _inProgress.value = false
    }

    private fun failTurn(failure: ApiResult.Failure) {
        val text = when (failure.kind) {
            ApiResult.Kind.MissingKey -> failure.message
            ApiResult.Kind.RateLimited -> failure.retryAfterSeconds
                ?.let { "Rate limited — retry after ${it}s" }
                ?: "Rate limited — try again shortly"

            ApiResult.Kind.Network -> "Network error — check connection"
            else -> "AI request failed: ${failure.message}"
        }
        updateAssistant("⚠️ $text")
        _inProgress.value = false
    }
}
