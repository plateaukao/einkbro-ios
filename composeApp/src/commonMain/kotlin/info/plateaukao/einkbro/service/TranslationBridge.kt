package info.plateaukao.einkbro.service

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.TranslateRepository
import info.plateaukao.einkbro.data.remote.toUserMessage
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native side of the einkbroGetTranslation channel (Android's
 * JsWebInterface.getTranslation): text_node_monitor.js posts
 * {text, elementId, callback}; this translates the text with the selected
 * provider and calls the JS callback with the result. Failures still invoke
 * the callback (empty string) so the JS side can clear its in-flight flag.
 */
class TranslationBridge(
    private val config: ConfigManager = AppServices.config,
) {
    private val translateRepository by lazy { TranslateRepository() }
    private val openAiRepository by lazy { OpenAiRepository() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /** Provider used for paragraph translation; set when translation starts. */
    var translateApi: TRANSLATE_API = TRANSLATE_API.GOOGLE

    // Request-rate control, mirroring Android: 4 concurrent for the fast
    // providers, strictly serial + 1500ms spacing for Gemini.
    private val fastSemaphore = Semaphore(4)
    private val slowSemaphore = Semaphore(1)

    private val cache = mutableMapOf<String, String>()

    @Serializable
    private data class TranslationRequest(
        val text: String = "",
        val elementId: String = "",
        val callback: String = "",
    )

    fun attach(engine: WebViewEngine) {
        engine.addMessageHandler("einkbroGetTranslation") { body ->
            val request = runCatching {
                json.decodeFromString<TranslationRequest>(body)
            }.getOrNull() ?: return@addMessageHandler
            if (request.text.isBlank() || request.callback.isBlank()) return@addMessageHandler
            scope.launch { handle(engine, request) }
        }
    }

    private suspend fun handle(engine: WebViewEngine, request: TranslationRequest) {
        val language = config.translation.translationLanguage.value
        val cacheKey = "$language|${request.text}"
        if (request.text.length < CACHE_TEXT_LENGTH_LIMIT) {
            cache[cacheKey]?.let {
                invokeCallback(engine, request, it)
                return
            }
        }

        val semaphore =
            if (translateApi == TRANSLATE_API.GEMINI) slowSemaphore else fastSemaphore
        semaphore.withPermit {
            val translated = performTranslation(request.text, language)
            if (translated.isNotEmpty() && request.text.length < CACHE_TEXT_LENGTH_LIMIT) {
                cache[cacheKey] = translated
            }
            invokeCallback(engine, request, translated)
            if (translateApi == TRANSLATE_API.GEMINI) {
                delay(1500)
            }
        }
    }

    private suspend fun performTranslation(text: String, language: String): String =
        when (translateApi) {
            TRANSLATE_API.GOOGLE -> translateRepository.gTranslateWithApi(text, language).orEmpty()
            TRANSLATE_API.OPENAI -> translateWithLlm(text, language, GptActionType.OpenAi, config.ai.gptModel)
            TRANSLATE_API.GEMINI ->
                translateWithLlm(text, language, GptActionType.Gemini, config.ai.geminiModel)

            else -> ""
        }

    private suspend fun translateWithLlm(
        text: String,
        language: String,
        actionType: GptActionType,
        model: String,
    ): String {
        val action = ChatGPTActionInfo(
            userMessage = "translate following content to $language; no other extra explanation:\n",
            actionType = actionType,
            model = model,
        )
        val messages = listOf((action.userMessage + text).toUserMessage())
        return if (actionType == GptActionType.Gemini) {
            openAiRepository.queryGemini(messages, action).valueOrNull().orEmpty()
        } else {
            openAiRepository.chatCompletion(messages, action)
                ?.choices?.firstOrNull()?.message?.content.orEmpty()
        }
    }

    private suspend fun invokeCallback(
        engine: WebViewEngine,
        request: TranslationRequest,
        translated: String,
    ) {
        val js = "${request.callback}(" +
            "'${escapeForJs(request.elementId)}', " +
            "'${escapeForJs(request.text)}', " +
            "'${escapeForJs(translated)}')"
        // WKWebView requires main-thread access.
        withContext(Dispatchers.Main) {
            engine.evaluateJavascript(js)
        }
    }

    private fun escapeForJs(text: String): String =
        text.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    companion object {
        private const val CACHE_TEXT_LENGTH_LIMIT = 15
    }
}
