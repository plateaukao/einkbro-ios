package info.plateaukao.einkbro.data.remote

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.preference.ReasoningEffort
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Ktor port of Android's OpenAiRepository: OpenAI-compatible chat completion
 * (blocking + SSE streaming) and Gemini generateContent. TTS-over-OpenAI and
 * tool-calling arrive with the features that use them.
 */
class OpenAiRepository(
    private val config: ConfigManager = AppServices.config,
) {
    private val client = HttpClientProvider.client
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var streamJob: Job? = null

    fun cancel() {
        streamJob?.cancel()
        streamJob = null
    }

    /**
     * OpenAI-compatible text-to-speech (parity Phase L). Returns the mp3 bytes
     * for [text], or null on any failure. Honors the self-hosted server URL so
     * an OpenAI-compatible TTS endpoint can be used; otherwise api.openai.com.
     */
    suspend fun tts(text: String): ByteArray? = try {
        val serverUrl = if (config.ai.useCustomGptUrl) config.ai.gptUrl else "https://api.openai.com"
        val response = client.post("$serverUrl$TTS_PATH") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.ai.gptApiKey}")
            setBody(
                json.encodeToString(
                    TTSRequest.serializer(),
                    TTSRequest(
                        input = text,
                        model = config.ai.gptVoiceModel,
                        voice = config.ai.gptVoiceOption.name.lowercase(),
                        speed = config.tts.ttsSpeedValue / 100.0,
                        instructions = config.ai.gptVoicePrompt.ifBlank { null },
                    ),
                )
            )
        }
        if (response.status.value != 200) null else response.body<ByteArray>()
    } catch (e: Exception) {
        null
    }

    /** Chat request with the resolved reasoning effort applied (Android
     *  createCompletionRequest). The enable_thinking pair is self-hosted-only:
     *  api.openai.com rejects requests with parameters it doesn't know. */
    private fun buildChatRequest(
        messages: List<ChatMessage>,
        gptActionInfo: ChatGPTActionInfo,
        stream: Boolean = false,
    ): ChatRequest {
        val effort = config.ai.resolveReasoningEffort(gptActionInfo)
        val isSelfHosted = gptActionInfo.actionType == GptActionType.SelfHosted
        val enableThinking = if (isSelfHosted) effort.toEnableThinking() else null
        return ChatRequest(
            model = gptActionInfo.model,
            messages = messages,
            stream = stream,
            reasoningEffort = effort.toOpenAiEffort(),
            enableThinking = enableThinking,
            chatTemplateKwargs = enableThinking?.let { ChatTemplateKwargs(it) },
        )
    }

    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        gptActionInfo: ChatGPTActionInfo,
    ): ChatCompletion? = try {
        val response = client.post("${getServerUrl(gptActionInfo.actionType)}$COMPLETION_PATH") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.ai.gptApiKey}")
            setBody(
                json.encodeToString(
                    ChatRequest.serializer(),
                    buildChatRequest(messages, gptActionInfo),
                )
            )
        }
        if (response.status.value != 200) null
        else json.decodeFromString(ChatCompletion.serializer(), response.bodyAsText())
    } catch (e: Exception) {
        null
    }

    fun chatStream(
        messages: List<ChatMessage>,
        gptActionInfo: ChatGPTActionInfo,
        appendResponseAction: (String) -> Unit,
        doneAction: () -> Unit = {},
        failureAction: (ApiResult.Failure) -> Unit,
    ) {
        if (gptActionInfo.actionType == GptActionType.Gemini) {
            // Gemini streaming (streamGenerateContent) is line-delimited JSON, not
            // SSE; the non-stream call is simpler and fast enough for e-ink use.
            streamJob?.cancel()
            streamJob = scope.launch {
                when (val result = queryGemini(messages, gptActionInfo)) {
                    is ApiResult.Success -> {
                        appendResponseAction(result.value)
                        doneAction()
                    }
                    is ApiResult.Failure -> failureAction(result)
                }
            }
            return
        }

        if (config.ai.gptApiKey.isEmpty() && gptActionInfo.actionType == GptActionType.OpenAi) {
            failureAction(ApiResult.Failure(ApiResult.Kind.MissingKey, "OpenAI API key not set"))
            return
        }

        streamJob?.cancel()
        streamJob = scope.launch {
            try {
                client.preparePost("${getServerUrl(gptActionInfo.actionType)}$COMPLETION_PATH") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer ${config.ai.gptApiKey}")
                    setBody(
                        json.encodeToString(
                            ChatRequest.serializer(),
                            buildChatRequest(messages, gptActionInfo, stream = true),
                        )
                    )
                }.execute { response ->
                    if (response.status.value != 200) {
                        failureAction(statusFailure(response.status.value))
                        return@execute
                    }
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") {
                            doneAction()
                            return@execute
                        }
                        if (data.isEmpty()) continue
                        try {
                            val delta = json.decodeFromString(ChatCompletionDelta.serializer(), data)
                            val content = delta.choices.firstOrNull()?.delta?.content
                            if (!content.isNullOrEmpty()) appendResponseAction(content)
                        } catch (e: Exception) {
                            failureAction(
                                ApiResult.Failure(ApiResult.Kind.Parse, "Could not parse AI response", cause = e)
                            )
                            return@execute
                        }
                    }
                    doneAction()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failureAction(
                    ApiResult.Failure(ApiResult.Kind.Network, e.message ?: "Network error", cause = e)
                )
            }
        }
    }

    suspend fun queryGemini(
        messages: List<ChatMessage>,
        gptActionInfo: ChatGPTActionInfo,
    ): ApiResult<String> {
        if (config.ai.geminiApiKey.isEmpty()) {
            return ApiResult.Failure(ApiResult.Kind.MissingKey, "Gemini API key not set")
        }
        return try {
            val model = gptActionInfo.model
            val response = client.post("$GEMINI_API_PREFIX$model:generateContent") {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", config.ai.geminiApiKey)
                // This is a single blocking response, so the whole generation has
                // to fit inside the timeout — there is no stream keeping the
                // socket fed. Summarizing a full video transcript is far past the
                // shared client's 2-minute default.
                timeout { requestTimeoutMillis = 300_000 }
                setBody(
                    json.encodeToString(
                        GeminiRequestData.serializer(),
                        GeminiRequestData(
                            contents = listOf(
                                GeminiContent(
                                    parts = listOf(
                                        GeminiContentPart(text = messages.joinToString(" ") { it.content })
                                    )
                                )
                            ),
                            safety_settings = listOf(
                                GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
                                GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
                                GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_ONLY_HIGH"),
                                GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE"),
                            ),
                            // Thought parts are filtered from the reply below, so
                            // includeThoughts only signals "still thinking" work.
                            generationConfig = config.ai.resolveReasoningEffort(gptActionInfo)
                                .toGeminiThinkingBudget()
                                ?.let {
                                    GeminiGenerationConfig(
                                        GeminiThinkingConfig(
                                            thinkingBudget = it,
                                            includeThoughts = it > 0,
                                        )
                                    )
                                },
                        ),
                    )
                )
            }
            if (response.status.value != 200) return statusFailure(response.status.value, "Gemini")
            val body = response.bodyAsText()
            val data = json.decodeFromString(GeminiResponseData.serializer(), body)
            // Every answer part, joined: Gemini splits a reply across parts, so
            // taking only the first truncated it (and returned the reasoning
            // summary outright when a thought part happened to come first).
            val text = data.candidates.firstOrNull()?.content?.parts
                ?.filterNot { it.thought }
                ?.joinToString("") { it.text }
            if (text.isNullOrEmpty()) ApiResult.Failure(ApiResult.Kind.Parse, "Gemini returned no content")
            else ApiResult.Success(text)
        } catch (e: Exception) {
            ApiResult.Failure(ApiResult.Kind.Network, e.message ?: "Network error", cause = e)
        }
    }

    /**
     * One-shot config check for the settings screens: sends a trivial prompt with the
     * given engine's key/model and reports the concrete failure (HTTP status, parse,
     * network) instead of a bare null, so the user can verify a key or model name
     * right after entering it.
     */
    suspend fun testConnection(gptActionInfo: ChatGPTActionInfo): ApiResult<String> {
        val messages = listOf(ChatMessage(TEST_PROMPT, ChatRole.User))
        if (gptActionInfo.actionType == GptActionType.Gemini) {
            return queryGemini(messages, gptActionInfo)
        }
        return try {
            val response = client.post("${getServerUrl(gptActionInfo.actionType)}$COMPLETION_PATH") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${config.ai.gptApiKey}")
                setBody(
                    json.encodeToString(
                        ChatRequest.serializer(),
                        buildChatRequest(messages, gptActionInfo),
                    )
                )
            }
            if (response.status.value != 200) return statusFailure(response.status.value)
            val text = json.decodeFromString(ChatCompletion.serializer(), response.bodyAsText())
                .choices.firstOrNull()?.message?.content
            if (text.isNullOrBlank()) ApiResult.Failure(ApiResult.Kind.Parse, "Empty response")
            else ApiResult.Success(text)
        } catch (e: Exception) {
            ApiResult.Failure(ApiResult.Kind.Network, e.message ?: "Network error", cause = e)
        }
    }

    private fun statusFailure(code: Int, provider: String = "AI provider"): ApiResult.Failure = when {
        code == 429 -> ApiResult.Failure(ApiResult.Kind.RateLimited, "$provider rate limit reached")
        code == 401 || code == 403 ->
            ApiResult.Failure(ApiResult.Kind.MissingKey, "$provider rejected the API key")
        code in 500..599 -> ApiResult.Failure(ApiResult.Kind.ServerError, "$provider error ($code)")
        else -> ApiResult.Failure(ApiResult.Kind.Unknown, "$provider request failed ($code)")
    }

    private fun getServerUrl(gptActionType: GptActionType): String =
        if (gptActionType == GptActionType.SelfHosted) config.ai.gptUrl
        else "https://api.openai.com"

    internal fun ReasoningEffort.toOpenAiEffort(): String? = when (this) {
        ReasoningEffort.Default -> null
        ReasoningEffort.Off -> "none"
        ReasoningEffort.Low -> "low"
        ReasoningEffort.Medium -> "medium"
        ReasoningEffort.High -> "high"
    }

    internal fun ReasoningEffort.toEnableThinking(): Boolean? = when (this) {
        ReasoningEffort.Default -> null
        ReasoningEffort.Off -> false
        else -> true
    }

    // thinkingBudget in tokens: 0 disables thinking; the tiers follow the
    // low/medium/high budgets Google uses for its own effort mapping.
    private fun ReasoningEffort.toGeminiThinkingBudget(): Int? = when (this) {
        ReasoningEffort.Default -> null
        ReasoningEffort.Off -> 0
        ReasoningEffort.Low -> 1024
        ReasoningEffort.Medium -> 8192
        ReasoningEffort.High -> 24576
    }

    companion object {
        private const val COMPLETION_PATH = "/v1/chat/completions"
        private const val TTS_PATH = "/v1/audio/speech"
        private const val TEST_PROMPT = "Reply with one word: ok"
        private const val GEMINI_API_PREFIX =
            "https://generativelanguage.googleapis.com/v1beta/models/"
    }
}
