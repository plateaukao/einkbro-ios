package info.plateaukao.einkbro.viewmodel

// Persisted by ordinal (AiConfig.externalSearchMethod), so readers must bounds-check:
// Papago and DeepL were removed from the middle of this list.
enum class TRANSLATE_API {
    GOOGLE, NAVER, LLM, OPENAI, GEMINI,
}

fun String.unescape(): String {
    return this.replace("\\\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\\\", "\\")
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
}
