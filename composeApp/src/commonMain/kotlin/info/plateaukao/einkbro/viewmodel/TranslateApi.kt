package info.plateaukao.einkbro.viewmodel

enum class TRANSLATE_API {
    GOOGLE, PAPAGO, NAVER, LLM, DEEPL, OPENAI, GEMINI,
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
