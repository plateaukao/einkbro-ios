package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.FunctionCall
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.ToolCall
import info.plateaukao.einkbro.data.remote.ToolChatMessage
import info.plateaukao.einkbro.data.remote.chatWithTools
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The free-form browser agent (Android ChatWebInterface.agentLoop): an LLM
 * tool-calling loop over the [BrowserTools] surface. Unlike the one-shot task
 * it replaced, the [history] lives for the whole conversation — each
 * [runTurn] appends the user's message and loops until the model answers in
 * plain text or calls `finish`, so the user can keep replying (parity with
 * Android's agent chat tab). Output flows through the tools' progress/finish
 * sinks, which the owning chat session renders as bubbles.
 */
class AgentSession(private val tools: BrowserTools) {

    private val json = Json { ignoreUnknownKeys = true }
    private val openAi = OpenAiRepository()
    private val config = AppServices.config

    // Whole-conversation tool-calling transcript (Android's toolHistory field).
    private val history = mutableListOf<ToolChatMessage>()

    fun dispose() = tools.dispose()

    suspend fun runTurn(userMessage: String) {
        // First turn seeds the system prompt with the originating-page hint so
        // the model can orient itself without a tool call.
        if (history.isEmpty()) {
            history += ToolChatMessage(
                role = "system",
                content = AgentToolSchema.SYSTEM_PROMPT + buildSnapshotHint(tools),
            )
        }
        history += ToolChatMessage(role = "user", content = userMessage)

        val actionInfo = agentActionInfo()

        var iter = 0
        while (iter < MAX_AGENT_ITERATIONS) {
            iter++
            val resp = openAi.chatWithTools(history, AgentToolSchema.tools, actionInfo)
            if (resp == null) {
                tools.error("LLM call failed on step $iter")
                return
            }
            val msg = resp.choices.firstOrNull()?.message
            if (msg == null) {
                tools.error("empty response")
                return
            }
            val toolCalls = msg.toolCalls.orEmpty()
            if (toolCalls.isEmpty()) {
                val text = msg.content.orEmpty().ifBlank { "(no response)" }
                history += ToolChatMessage(role = "assistant", content = text)
                tools.finish(text)
                return
            }

            history += ToolChatMessage(
                role = "assistant",
                content = msg.content,
                toolCalls = toolCalls,
            )
            for (call in toolCalls) {
                tools.tool("${call.function.name} ${call.function.arguments.take(120)}")
                val result = dispatch(call, tools)
                history += ToolChatMessage(
                    role = "tool",
                    toolCallId = call.id,
                    content = result,
                )
                if (call.function.name == "finish") return
            }
        }
        tools.finish("(turn did not complete within $MAX_AGENT_ITERATIONS steps)")
    }

    private suspend fun dispatch(call: ToolCall, tools: BrowserTools): String { return try {
        val args: JsonObject = runCatching {
            json.parseToJsonElement(call.function.arguments.ifBlank { "{}" }).jsonObject
        }.getOrElse { return "error: invalid JSON arguments: ${it.message}" }

        fun str(key: String): String? = args[key]?.jsonPrimitive?.contentOrNull
        fun window(content: String) = ToolTextWindow.window(
            content = content,
            maxChars = MAX_TOOL_RESULT_CHARS,
            offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0,
            search = args["search"]?.jsonPrimitive?.contentOrNull,
        )

        when (call.function.name) {
            "get_initial_page_links" -> encodeLinks(tools.initialPageLinks())
            "read_initial_page" -> tools.initialPageText().trim()
                .ifBlank { "error: no initial page text captured" }.let { if (it.startsWith("error:")) it else window(it) }
            "open_url" -> {
                val url = str("url") ?: return "error: missing url"
                if (tools.openUrlInBg(url)) "ok: loaded $url" else "error: failed to load $url"
            }
            "web_search" -> {
                val query = str("query")?.takeIf { it.isNotBlank() } ?: return "error: missing query"
                if (!tools.searchInBg(query)) "error: search results page failed to load"
                else encodeLinks(tools.currentBgPageLinks())
            }
            "run_javascript" -> {
                val code = str("code")?.takeIf { it.isNotBlank() } ?: return "error: missing code"
                val target = str("target") ?: "tab"
                val result = when (target) {
                    "background" -> tools.runJavascriptInBg(code)
                        ?: "error: no page loaded — call open_url first"
                    else -> tools.runJavascriptInInitialTab(code)
                        ?: "error: the originating tab is no longer available"
                }
                if (result.length > MAX_TOOL_RESULT_CHARS) window(result) else result
            }
            "save_epub" -> {
                val bookTitle = str("book_title")?.takeIf { it.isNotBlank() }
                    ?: return "error: missing book_title"
                val specs = (args["chapters"] as? JsonArray).orEmpty().mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    BrowserTools.EpubChapterSpec(
                        url = url,
                        title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
                if (specs.isEmpty()) return "error: chapters array has no valid entries"
                var loaded = 0
                val location = tools.saveEpub(bookTitle, specs) { index, total, title ->
                    loaded++
                    tools.info("chapter $index/$total: $title")
                }
                when {
                    location == null -> "error: failed to save epub"
                    loaded < specs.size -> "ok: saved $loaded of ${specs.size} chapters to $location"
                    else -> "ok: saved $loaded chapters to $location"
                }
            }
            "read_current_page" -> tools.currentBgPageText().trim()
                .ifBlank { "error: no page loaded or page body is empty" }
                .let { if (it.startsWith("error:")) it else window(it) }
            "get_page_links" -> encodeLinks(tools.currentBgPageLinks())
            "note" -> {
                str("text")?.takeIf { it.isNotBlank() }?.let { tools.info(it) }
                "ok"
            }
            "speak" -> {
                val text = str("text").orEmpty()
                if (text.isBlank()) "error: missing text"
                else {
                    tools.speak(text, str("title").orEmpty())
                    "ok: queued ${text.length} chars for TTS"
                }
            }
            "read_initial_html" -> tools.initialPageRawHtml()
                .ifBlank { "error: no initial page HTML captured" }
                .let { if (it.startsWith("error:")) it else window(it) }
            "read_page_source" -> {
                val url = str("url")?.takeIf { it.isNotBlank() } ?: tools.initialPageUrl()
                if (url.isBlank()) "error: no url available — pass a url argument"
                else when (val html = tools.fetchPageSource(url)) {
                    null -> "error: failed to download $url"
                    "" -> "error: empty document at $url"
                    else -> window(html)
                }
            }
            "get_domain_javascript" -> tools.getInitialDomainJavascript()
                .ifBlank { "(no postLoadJavascript saved for this host)" }
            "get_domain_css" -> tools.getInitialDomainCss()
                .ifBlank { "(no customCss saved for this host)" }
            "set_domain_javascript" -> {
                val code = str("code") ?: return "error: missing code"
                tools.setInitialDomainJavascript(code)
                if (code.isBlank()) "ok: cleared postLoadJavascript" else "ok: saved ${code.length} chars"
            }
            "set_domain_css" -> {
                val code = str("code") ?: return "error: missing code"
                tools.setInitialDomainCss(code)
                if (code.isBlank()) "ok: cleared customCss" else "ok: saved ${code.length} chars"
            }
            "finish" -> {
                tools.finish(str("summary").orEmpty())
                "ok"
            }
            else -> "error: unknown tool ${call.function.name}"
        }
    } catch (e: Exception) {
        "error: ${e.message}"
    } }

    private fun encodeLinks(links: List<BrowserTools.Link>): String {
        if (links.isEmpty()) return "[]"
        val array: JsonArray = buildJsonArray {
            links.take(MAX_LINKS_RETURNED).forEach { link ->
                add(buildJsonObject {
                    put("text", JsonPrimitive(link.text))
                    put("href", JsonPrimitive(link.href))
                })
            }
        }
        return array.toString()
    }

    private fun buildSnapshotHint(tools: BrowserTools): String {
        val url = tools.initialPageUrl()
        if (url.isBlank()) return ""
        return "\n\nThe user is currently viewing: \"${tools.initialPageTitle()}\" at $url."
    }

    private fun agentActionInfo(): ChatGPTActionInfo = ChatGPTActionInfo(
        name = "agent",
        systemMessage = AgentToolSchema.SYSTEM_PROMPT,
        userMessage = "",
        actionType = if (config.ai.useCustomGptUrl) GptActionType.SelfHosted else GptActionType.OpenAi,
        model = if (config.ai.useCustomGptUrl) config.ai.alternativeModel else config.ai.gptModel,
    )

    companion object {
        private const val MAX_AGENT_ITERATIONS = 12
        private const val MAX_TOOL_RESULT_CHARS = 8_000
        private const val MAX_LINKS_RETURNED = 50
    }
}
