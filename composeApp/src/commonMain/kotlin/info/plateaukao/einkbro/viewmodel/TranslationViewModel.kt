package info.plateaukao.einkbro.viewmodel

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionScope
import info.plateaukao.einkbro.util.TranslationLanguage
import info.plateaukao.einkbro.view.EBToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Stub of the Android TranslationViewModel: same public API surface as used by
 * the translate dialogs, but every network call is replaced with representative
 * sample data so the UI renders (and looks "just translated") without services.
 */
class TranslationViewModel(
    private val config: ConfigManager = AppServices.config,
) : ViewModel() {

    var gptActionInfo: ChatGPTActionInfo =
        config.ai.gptActionList.firstOrNull() ?: sampleGptActions.first()

    private val _responseMessage = MutableStateFlow(AnnotatedString(SAMPLE_TRANSLATED_TEXT))
    val responseMessage: StateFlow<AnnotatedString> = _responseMessage.asStateFlow()

    private val _inputMessage = MutableStateFlow(SAMPLE_SOURCE_TEXT)
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _messageWithContext = MutableStateFlow(SAMPLE_SOURCE_TEXT)
    val messageWithContext: StateFlow<String> = _messageWithContext.asStateFlow()

    private val _translationLanguage = MutableStateFlow(config.translation.translationLanguage)
    val translationLanguage: StateFlow<TranslationLanguage> = _translationLanguage.asStateFlow()

    private val _sourceLanguage = MutableStateFlow(config.translation.sourceLanguage)
    val sourceLanguage: StateFlow<TranslationLanguage> = _sourceLanguage.asStateFlow()

    private val _rotateResultScreen = MutableStateFlow(false)
    val rotateResultScreen: StateFlow<Boolean> = _rotateResultScreen.asStateFlow()

    private val _translateMethod = MutableStateFlow(TRANSLATE_API.GOOGLE)
    val translateMethod: StateFlow<TRANSLATE_API> = _translateMethod.asStateFlow()

    private val _showEditDialogWithIndex = MutableStateFlow(-1)
    val showEditDialogWithIndex: StateFlow<Int> = _showEditDialogWithIndex.asStateFlow()

    private val _scrollSignal = MutableSharedFlow<Boolean>()
    val scrollSignal: SharedFlow<Boolean> = _scrollSignal.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    var url: String = "https://example.com/article"
    var pageTitle: String = "Sample Article"

    fun updateRotateResultScreen(rotate: Boolean) {
        _rotateResultScreen.value = rotate
    }

    fun updateTranslateMethod(translateApi: TRANSLATE_API) {
        _translateMethod.value = translateApi
    }

    fun hasOpenAiApiKey(): Boolean = config.ai.gptApiKey.isNotBlank()

    fun updateMessageWithContext(userMessage: String) {
        _messageWithContext.value = userMessage
    }

    fun updateInputMessage(userMessage: String) {
        _inputMessage.value = userMessage
        _responseMessage.value = AnnotatedString("...")
    }

    fun updateTranslationLanguage(language: TranslationLanguage) {
        _translationLanguage.value = language
    }

    fun updateTranslationLanguageAndGo(language: TranslationLanguage) {
        updateTranslationLanguage(language)
        translate(_translateMethod.value)
    }

    fun updateSourceLanguage(language: TranslationLanguage) {
        _sourceLanguage.value = language
    }

    fun updateSourceLanguageAndGo(translateApi: TRANSLATE_API, language: TranslationLanguage) {
        updateSourceLanguage(language)
        translate(translateApi)
    }

    /** The Naver dict result is rendered in a WebView on Android; not in the catalog. */
    fun isWebViewStyle(): Boolean = false

    /**
     * Fakes a translation round-trip: shows "..." briefly, then a canned result
     * labeled with the chosen provider so taps on provider icons stay visible.
     */
    fun translate(
        translateApi: TRANSLATE_API = _translateMethod.value,
        userMessage: String? = null,
    ) {
        _translateMethod.value = translateApi
        if (userMessage != null) {
            _inputMessage.value = userMessage
        }
        _responseMessage.value = AnnotatedString("...")
        viewModelScope.launch {
            delay(400)
            _responseMessage.value = AnnotatedString(
                when (translateApi) {
                    TRANSLATE_API.LLM -> "[${gptActionInfo.name}]\n$SAMPLE_GPT_RESPONSE"
                    else -> "$SAMPLE_TRANSLATED_TEXT\n\n(${translateApi.name} sample result)"
                }
            )
        }
    }

    fun getGptActionList(): List<ChatGPTActionInfo> =
        config.ai.gptActionList.ifEmpty { sampleGptActions }

    fun cancel() {
        // no in-flight request in the stub
    }

    fun setupGptAction(gptAction: ChatGPTActionInfo) {
        updateTranslateMethod(TRANSLATE_API.LLM)
        gptActionInfo = gptAction
    }

    fun setupTextSummary(text: String): Boolean {
        updateInputMessage(text)
        setupGptAction(sampleGptActions.first())
        return true
    }

    fun showEditGptActionDialog(gptActionInfoIndex: Int) {
        _showEditDialogWithIndex.value = gptActionInfoIndex
        EBToast.show(AppServices.context, "would edit GPT action #$gptActionInfoIndex")
    }

    fun resetEditDialogIndex() {
        _showEditDialogWithIndex.value = -1
    }

    suspend fun saveTranslationResult() {
        EBToast.show(AppServices.context, "translation result saved")
    }

    fun emitScrollEvent(isUp: Boolean) {
        viewModelScope.launch {
            _scrollSignal.emit(isUp)
        }
    }

    companion object {
        const val SAMPLE_SOURCE_TEXT =
            "오늘은 날씨가 참 좋아서 공원에 산책하러 갔어요."
        const val SAMPLE_TRANSLATED_TEXT =
            "The weather was so nice today that I went for a walk in the park."
        const val SAMPLE_GPT_RESPONSE =
            "Here is a natural translation of the selected text:\n\n" +
                    "\"The weather was so nice today that I went for a walk in the park.\"\n\n" +
                    "Notes: 산책하러 가다 means \"to go for a walk\"."

        val sampleGptActions: List<ChatGPTActionInfo> = listOf(
            ChatGPTActionInfo(name = "Translate", scope = GptActionScope.TextSelection),
            ChatGPTActionInfo(name = "Explain", scope = GptActionScope.TextSelection),
            ChatGPTActionInfo(name = "Summarize", scope = GptActionScope.WholePage),
        )
    }
}
