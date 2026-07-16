package info.plateaukao.einkbro.preference

import kotlinx.serialization.Serializable

@Serializable
data class ChatGPTActionInfo (
    val name: String = "ChatGPT",
    val systemMessage: String = "",
    val userMessage: String = "",
    val actionType: GptActionType = GptActionType.Default,
    val model: String = "",
    val display: GptActionDisplay = GptActionDisplay.Popup,
    val scope: GptActionScope = GptActionScope.TextSelection,
    val id: String = kotlin.random.Random.nextBytes(16).joinToString("") { b -> b.toUByte().toString(16).padStart(2, '0') },
)

@Serializable
enum class GptActionType {
    Default,
    OpenAi,
    SelfHosted,
    Gemini
}

@Serializable
enum class GptActionDisplay {
    Popup,
    NewTab,
    SplitScreen,
}

@Serializable
enum class GptActionScope {
    TextSelection,
    WholePage,
}
