package info.plateaukao.einkbro.viewmodel

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.ApiResult
import info.plateaukao.einkbro.data.remote.ChatMessage
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.TranslateRepository
import info.plateaukao.einkbro.data.remote.toSystemMessage
import info.plateaukao.einkbro.data.remote.toUserMessage
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.unit.HelperUnit
import info.plateaukao.einkbro.util.TranslationLanguage
import info.plateaukao.einkbro.view.EBToast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real port of the Android TranslationViewModel: Google/DeepL/Papago text
 * translation plus LLM (OpenAI-compatible + Gemini) queries drive the
 * translate popup. Android-only surfaces (Naver dict WebView, image translate,
 * task streams) are deferred to later phases.
 */
class TranslationViewModel(
    private val config: ConfigManager = AppServices.config,
) : ViewModel() {

    private val translateRepository = TranslateRepository()
    private val openAiRepository = OpenAiRepository()

    var gptActionInfo: ChatGPTActionInfo =
        config.ai.gptActionForExternalSearch ?: config.ai.gptActionList.firstOrNull()
        ?: ChatGPTActionInfo()

    private val _responseMessage = MutableStateFlow(AnnotatedString(""))
    val responseMessage: StateFlow<AnnotatedString> = _responseMessage.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _messageWithContext = MutableStateFlow("")
    val messageWithContext: StateFlow<String> = _messageWithContext.asStateFlow()

    private val _translationLanguage = MutableStateFlow(config.translation.translationLanguage)
    val translationLanguage: StateFlow<TranslationLanguage> = _translationLanguage.asStateFlow()

    private val _sourceLanguage = MutableStateFlow(config.translation.sourceLanguage)
    val sourceLanguage: StateFlow<TranslationLanguage> = _sourceLanguage.asStateFlow()

    private val _rotateResultScreen = MutableStateFlow(false)
    val rotateResultScreen: StateFlow<Boolean> = _rotateResultScreen.asStateFlow()

    private val _translateMethod = MutableStateFlow(config.ai.externalSearchMethod)
    val translateMethod: StateFlow<TRANSLATE_API> = _translateMethod.asStateFlow()

    private val _showEditDialogWithIndex = MutableStateFlow(-1)
    val showEditDialogWithIndex: StateFlow<Int> = _showEditDialogWithIndex.asStateFlow()

    private val _scrollSignal = MutableSharedFlow<Boolean>()
    val scrollSignal: SharedFlow<Boolean> = _scrollSignal.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    var url: String = ""
    var pageTitle: String = ""

    fun updateRotateResultScreen(rotate: Boolean) {
        _rotateResultScreen.value = rotate
    }

    fun updateTranslateMethod(translateApi: TRANSLATE_API) {
        _translateMethod.value = translateApi
        config.ai.externalSearchMethod = translateApi
    }

    fun hasOpenAiApiKey(): Boolean = config.ai.gptApiKey.isNotBlank()

    fun updateMessageWithContext(userMessage: String) {
        _messageWithContext.value = userMessage.unescape()
    }

    fun updateInputMessage(userMessage: String) {
        _inputMessage.value = userMessage.unescape()
        _responseMessage.value = AnnotatedString("...")
    }

    fun updateTranslationLanguage(language: TranslationLanguage) {
        _translationLanguage.value = language
        config.translation.translationLanguage = language
    }

    fun updateTranslationLanguageAndGo(language: TranslationLanguage) {
        updateTranslationLanguage(language)
        translate(_translateMethod.value)
    }

    fun updateSourceLanguage(language: TranslationLanguage) {
        _sourceLanguage.value = language
        config.translation.sourceLanguage = language
    }

    fun updateSourceLanguageAndGo(translateApi: TRANSLATE_API, language: TranslationLanguage) {
        updateSourceLanguage(language)
        translate(translateApi)
    }

    /** The Naver dict result renders in a WebView on Android; not ported. */
    fun isWebViewStyle(): Boolean = false

    fun translate(
        translateApi: TRANSLATE_API = _translateMethod.value,
        userMessage: String? = null,
    ) {
        _translateMethod.value = translateApi
        config.ai.externalSearchMethod = translateApi
        _responseMessage.value = AnnotatedString("...")

        if (userMessage != null) {
            _inputMessage.value = userMessage
        }

        when (translateApi) {
            TRANSLATE_API.GOOGLE -> callGoogleTranslate()
            TRANSLATE_API.PAPAGO -> callPapagoTranslate()
            TRANSLATE_API.DEEPL -> callDeepLTranslate()
            TRANSLATE_API.LLM -> queryLlm()
            else -> Unit
        }
    }

    fun getGptActionList(): List<ChatGPTActionInfo> = config.ai.gptActionList

    fun cancel() = openAiRepository.cancel()

    fun setupGptAction(gptAction: ChatGPTActionInfo) {
        updateTranslateMethod(TRANSLATE_API.LLM)
        gptActionInfo = gptAction
    }

    fun setupTextSummary(text: String): Boolean {
        updateInputMessage(text)
        setupGptAction(
            ChatGPTActionInfo(
                name = "Summarize",
                systemMessage = config.ai.gptUserPromptForWebPage,
                actionType = config.ai.gptForSummary,
                model = config.ai.getGptTypeModelMap()[config.ai.gptForSummary] ?: config.ai.gptModel,
            )
        )
        return true
    }

    private fun callGoogleTranslate() {
        val message = _inputMessage.value
        viewModelScope.launch {
            val result = translateRepository.gTranslateWithApi(
                message,
                targetLanguage = config.translation.translationLanguage.value,
            )
            if (result.isNullOrEmpty()) emitTranslationError("Google")
            else _responseMessage.value = AnnotatedString(result)
        }
    }

    private fun callDeepLTranslate() {
        val message = _inputMessage.value
        viewModelScope.launch {
            val result = translateRepository.deepLTranslate(
                message,
                targetLanguage = config.translation.translationLanguage,
            )
            if (result.isNullOrEmpty()) emitTranslationError("DeepL")
            else _responseMessage.value = AnnotatedString(result)
        }
    }

    private fun callPapagoTranslate() {
        val message = _inputMessage.value
        viewModelScope.launch {
            val result = translateRepository.pTranslate(
                message,
                targetLanguage = config.translation.translationLanguage.value,
            )
            if (result.isNullOrEmpty()) emitTranslationError("Papago")
            else _responseMessage.value = AnnotatedString(result)
        }
    }

    private fun emitTranslationError(provider: String) {
        val text = "$provider translation failed — check connection or switch provider"
        _responseMessage.value = AnnotatedString(text)
        viewModelScope.launch { _errorFlow.emit(text) }
    }

    private var toBeSavedResponseString = ""

    private fun queryLlm() {
        _translateMethod.value = TRANSLATE_API.LLM
        config.ai.gptActionForExternalSearch = gptActionInfo

        val messages = mutableListOf<ChatMessage>()
        if (gptActionInfo.systemMessage.isNotBlank()) {
            messages.add(gptActionInfo.systemMessage.toSystemMessage())
        }
        val (promptPrefix, selectedText) = getSelectedTextAndPromptPrefix()
        messages.add("$promptPrefix$selectedText".toUserMessage())

        viewModelScope.launch {
            queryLlm(messages, getExactActionInfo(gptActionInfo))
        }
    }

    private fun getExactActionInfo(gptActionInfo: ChatGPTActionInfo): ChatGPTActionInfo =
        if (gptActionInfo.actionType == GptActionType.Default) {
            ChatGPTActionInfo(
                actionType = config.ai.getDefaultActionType(),
                model = config.ai.getDefaultActionModel(),
                name = gptActionInfo.name,
                userMessage = gptActionInfo.userMessage,
                systemMessage = gptActionInfo.systemMessage,
            )
        } else gptActionInfo

    suspend fun queryLlm(messages: MutableList<ChatMessage>, gptActionInfo: ChatGPTActionInfo) {
        if (config.ai.enableOpenAiStream) {
            queryWithStream(messages, gptActionInfo)
            return
        }

        if (gptActionInfo.actionType == GptActionType.Gemini) {
            when (val result = openAiRepository.queryGemini(messages, gptActionInfo)) {
                is ApiResult.Success -> {
                    toBeSavedResponseString = result.value
                    _responseMessage.value = HelperUnit.parseMarkdown(result.value)
                }
                is ApiResult.Failure -> emitFailure(result)
            }
            return
        }

        val chatCompletion = openAiRepository.chatCompletion(messages, gptActionInfo)
        val responseContent = chatCompletion?.choices
            ?.firstOrNull { it.message.role == ChatRole.Assistant }?.message?.content
        if (responseContent == null) {
            _responseMessage.value = AnnotatedString("Something went wrong.")
        } else {
            toBeSavedResponseString = responseContent.replace("<think>\n\n</think>\n\n", "")
            _responseMessage.value = HelperUnit.parseMarkdown(toBeSavedResponseString)
        }
    }

    private fun queryWithStream(messages: MutableList<ChatMessage>, gptActionInfo: ChatGPTActionInfo) {
        var responseString = ""
        openAiRepository.chatStream(
            messages,
            gptActionInfo,
            appendResponseAction = {
                responseString += it
                toBeSavedResponseString = responseString.unescape()
                _responseMessage.value = HelperUnit.parseMarkdown(toBeSavedResponseString)
            },
            doneAction = { },
            failureAction = { failure -> emitFailure(failure) },
        )
    }

    private fun emitFailure(failure: ApiResult.Failure) {
        val text = when (failure.kind) {
            ApiResult.Kind.MissingKey -> failure.message
            ApiResult.Kind.RateLimited -> failure.retryAfterSeconds
                ?.let { "Rate limited — retry after ${it}s" }
                ?: "Rate limited — try again in a moment"

            ApiResult.Kind.Network -> "Network error — check connection"
            ApiResult.Kind.ServerError, ApiResult.Kind.Parse, ApiResult.Kind.Unknown ->
                "AI request failed: ${failure.message}"
        }
        toBeSavedResponseString = text
        _responseMessage.value = AnnotatedString(text)
        viewModelScope.launch { _errorFlow.emit(text) }
    }

    private fun getSelectedTextAndPromptPrefix(): Pair<String, String> {
        val promptPrefix = gptActionInfo.userMessage
        val selectedText = if (promptPrefix.contains("<<") &&
            promptPrefix.contains(">>") &&
            _messageWithContext.value.contains("<<") &&
            _messageWithContext.value.contains(">>")
        ) {
            _messageWithContext.value
        } else {
            _inputMessage.value
        }
        return Pair(promptPrefix, selectedText)
    }

    fun showEditGptActionDialog(gptActionInfoIndex: Int) {
        _showEditDialogWithIndex.value = gptActionInfoIndex
    }

    fun resetEditDialogIndex() {
        _showEditDialogWithIndex.value = -1
    }

    /** GPT-query persistence (ChatGptQuery table) arrives with Phase 7 data work. */
    suspend fun saveTranslationResult() {
        EBToast.show(AppServices.context, "saving results: later phase")
    }

    fun emitScrollEvent(isUp: Boolean) {
        viewModelScope.launch {
            _scrollSignal.emit(isUp)
        }
    }
}
