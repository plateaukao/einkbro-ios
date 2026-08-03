package info.plateaukao.einkbro.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// OpenAI-compatible chat types, ported from Android's OpenAiRepository.

@Serializable
data class ChatCompletion(
    val id: String = "",
    val created: Int = 0,
    val model: String = "",
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage = ChatUsage(0, 0, 0),
)

@Serializable
data class ChatCompletionDelta(
    val id: String = "",
    val created: Int = 0,
    val model: String = "",
    val choices: List<ChatChoiceDelta> = emptyList(),
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completeTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
)

@Serializable
data class ChatChoiceDelta(
    val index: Int = 0,
    val delta: ChatDelta = ChatDelta(),
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage,
)

@Serializable
enum class ChatRole {
    @SerialName("user")
    User,

    @SerialName("system")
    System,

    @SerialName("assistant")
    Assistant,
}

@Serializable
data class ChatDelta(
    val content: String? = null,
)

@Serializable
data class ChatMessage(
    val content: String,
    val role: ChatRole,
)

fun String.toUserMessage() = ChatMessage(content = this, role = ChatRole.User)
fun String.toSystemMessage() = ChatMessage(content = this, role = ChatRole.System)

/** OpenAI /v1/audio/speech request body (parity Phase L OpenAI-TTS). */
@Serializable
data class TTSRequest(
    val input: String,
    val model: String,
    val voice: String,
    val speed: Double = 1.0,
    val instructions: String? = null,
)

// ── Gemini generateContent types ──────────────────────────────────────────

@Serializable
data class GeminiRequestData(
    val contents: List<GeminiContent>,
    val safety_settings: List<GeminiSafetySetting> = emptyList(),
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiContentPart>,
)

/**
 * Serves both request and response parts, as on Android.
 *
 * Gemini 3 marks reasoning-summary parts with `thought=true`, and attaches a
 * `thoughtSignature` to the final answer part — which may carry no `text` key at
 * all. So [text] must default, or a signature-only part fails to decode and the
 * whole response surfaces as a bogus network error.
 *
 * Both defaults are safe to send: the encoder runs with `encodeDefaults = false`
 * (kotlinx's default), so a request part emits `text` only, never `thought`.
 */
@Serializable
data class GeminiContentPart(
    val text: String = "",
    val thought: Boolean = false,
)

@Serializable
data class GeminiSafetySetting(
    val category: String,
    val threshold: String,
)

@Serializable
data class GeminiResponseData(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    // STOP, MAX_TOKENS, SAFETY, ... — set once the model is done talking.
    val finishReason: String? = null,
)
