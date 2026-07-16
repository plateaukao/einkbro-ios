package info.plateaukao.einkbro.viewmodel

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.ApiResult
import info.plateaukao.einkbro.data.remote.ChatMessage
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.toUserMessage
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.unit.HelperUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Chat with web" (parity Phase K). Android loads a `chat.html` page into a new
 * tab and drives it through [info.plateaukao.einkbro.browser.ChatWebInterface];
 * the iOS port renders a native Compose chat instead (cleaner over WKWebView and
 * fully drivable), but keeps the same conversation model: the page's raw text is
 * injected once as context, then each user turn streams an assistant reply from
 * the [gptForChatWeb]-configured engine. History is in-memory (matching Android's
 * ChatWebInterface); explicit AI-action results persist via
 * [TranslationViewModel.saveTranslationResult].
 */
class ChatWithWebViewModel(
    private val config: ConfigManager = AppServices.config,
) : ViewModel() {

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

    // API-facing history (user/assistant), separate from the UI bubbles.
    private val chatHistory = mutableListOf<ChatMessage>()

    fun hasApiKey(): Boolean =
        config.ai.gptApiKey.isNotBlank() || config.ai.geminiApiKey.isNotBlank() ||
            config.ai.useCustomGptUrl

    /** Start a fresh conversation with the given page as context. */
    fun start(content: String, title: String, url: String) {
        webContent = content
        webTitle = title
        webUrl = url
        chatHistory.clear()
        _messages.value = emptyList()
        _inProgress.value = false
    }

    fun cancel() {
        openAiRepository.cancel()
        _inProgress.value = false
    }

    /** Optionally fire an initial GPT action (used when opened from a page-AI action). */
    fun runInitialAction(action: ChatGPTActionInfo) {
        val prompt = action.userMessage.ifBlank { action.systemMessage }
        if (prompt.isNotBlank()) send(prompt)
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _inProgress.value) return

        val actionInfo = resolveActionInfo(trimmed)
        val userMsg = trimmed.toUserMessage()

        val apiMessages = mutableListOf<ChatMessage>().apply {
            add(createWebContentMessage(webContent))
            addAll(chatHistory)
            add(userMsg)
        }

        // Append the user bubble and an empty assistant bubble to stream into.
        _messages.value = _messages.value +
            ChatUiMessage(ChatRole.User, trimmed, AnnotatedString(trimmed)) +
            ChatUiMessage(ChatRole.Assistant, "", AnnotatedString(""))
        _inProgress.value = true

        val aggregate = StringBuilder()

        if (actionInfo.actionType == GptActionType.Gemini) {
            viewModelScope.launch {
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

    private fun updateAssistant(text: String) {
        val list = _messages.value.toMutableList()
        val idx = list.indexOfLast { it.role == ChatRole.Assistant }
        if (idx >= 0) {
            list[idx] = list[idx].copy(text = text, rendered = HelperUnit.parseMarkdown(text))
            _messages.value = list
        }
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
