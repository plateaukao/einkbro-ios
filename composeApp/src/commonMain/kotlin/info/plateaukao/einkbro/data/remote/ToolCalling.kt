package info.plateaukao.einkbro.data.remote

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.task.ToolDefinition
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// OpenAI function-calling messages: nullable `content`, a `tool_calls` array on
// assistant turns, and a `tool` role for results. Kept separate from ChatMessage
// so the streaming/Gemini paths stay untouched. (Android OpenAiRepository parity.)

@Serializable
data class ToolChatRequest(
    val model: String,
    val messages: List<ToolChatMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean = false,
    // Reasoning controls; null values are omitted from the JSON (explicitNulls
    // is off for this file's encoder). Android OpenAiRepository.chatWithTools parity.
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    @SerialName("enable_thinking") val enableThinking: Boolean? = null,
    @SerialName("chat_template_kwargs") val chatTemplateKwargs: ChatTemplateKwargs? = null,
)

@Serializable
data class ToolChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class ToolChatCompletion(
    val choices: List<ToolChatChoice>,
)

@Serializable
data class ToolChatChoice(
    val index: Int = 0,
    val message: ToolChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

/**
 * One tool-calling chat round trip. Returns null on any failure (the agent loop
 * treats null as "give up this turn"). OpenAI-compatible endpoints only —
 * Gemini has no equivalent, so the caller must gate on non-Gemini.
 */
suspend fun OpenAiRepository.chatWithTools(
    messages: List<ToolChatMessage>,
    tools: List<ToolDefinition>,
    gptActionInfo: ChatGPTActionInfo,
): ToolChatCompletion? = try {
    val config = AppServices.config
    val serverUrl = if (gptActionInfo.actionType == GptActionType.SelfHosted) {
        config.ai.gptUrl
    } else {
        "https://api.openai.com"
    }
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    // The enable_thinking pair is self-hosted-only: api.openai.com rejects
    // requests with parameters it doesn't know.
    val effort = config.ai.resolveReasoningEffort(gptActionInfo)
    val enableThinking =
        if (gptActionInfo.actionType == GptActionType.SelfHosted) effort.toEnableThinking() else null
    val payload = ToolChatRequest(
        model = gptActionInfo.model,
        messages = messages,
        tools = tools,
        toolChoice = "auto",
        reasoningEffort = effort.toOpenAiEffort(),
        enableThinking = enableThinking,
        chatTemplateKwargs = enableThinking?.let { ChatTemplateKwargs(it) },
    )
    val response = HttpClientProvider.client.post("$serverUrl/v1/chat/completions") {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer ${config.ai.gptApiKey}")
        setBody(json.encodeToString(ToolChatRequest.serializer(), payload))
    }
    if (response.status.value != 200) null
    else json.decodeFromString(ToolChatCompletion.serializer(), response.bodyAsText())
} catch (e: Exception) {
    null
}
