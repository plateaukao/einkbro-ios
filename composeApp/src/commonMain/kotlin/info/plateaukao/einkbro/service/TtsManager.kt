package info.plateaukao.einkbro.service

import info.plateaukao.einkbro.util.Locale

/**
 * System text-to-speech seam (AVSpeechSynthesizer on iOS). [readText] splits
 * the text into sentence chunks, queues them all, reports per-chunk progress,
 * and suspends until the last chunk finishes (or [stopReading] is called) —
 * the same contract as the Android TtsManager.
 */
expect class TtsManager() {
    fun setSpeechRate(rate: Float)
    fun getAvailableLanguages(): List<Locale>
    suspend fun readText(text: String, onProgress: (Int, Int, String) -> Unit)
    fun isSpeaking(): Boolean
    fun pause()
    fun resume()
    fun stopReading()
}

/**
 * Android's HelperUnit.processedTextToChunks: sentence-split, then merge
 * short sentences into chunks of at most ~60 words so utterances aren't tiny.
 */
fun processedTextToChunks(text: String): MutableList<String> {
    val processedText = stripNonSpokenContent(
        text.replace("\\n", " ")
            .replace("\\\"", "")
            .replace("\\t", "")
            .replace("\\", "")
    )
    val sentences = processedText.split("(?<=\\.)(?!\\d)|(?<=。)|(?<=？)|(?<=\\?)".toRegex())
    val chunks = sentences.fold(mutableListOf<String>()) { acc, sentence ->
        if (acc.isEmpty() || (acc.last() + sentence).getWordCount() > 60) {
            acc.add(sentence.trim())
        } else {
            val last = acc.last()
            acc[acc.size - 1] = "$last$sentence"
        }
        acc
    }
    return chunks.filter { it.isNotBlank() }.toMutableList()
}

// <script>/<style> bodies, then comments: their contents are removed with the
// element, otherwise the CSS or JS inside gets spelled out as words. `[\s\S]`
// rather than DOT_MATCHES_ALL so the pattern behaves the same on every target.
private val scriptElementRegex =
    Regex("<script\\b[^>]*>[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE)
private val styleElementRegex =
    Regex("<style\\b[^>]*>[\\s\\S]*?</style\\s*>", RegexOption.IGNORE_CASE)
private val commentRegex = Regex("<!--[\\s\\S]*?-->")

// Only `<` followed by a letter, `/` or `!` counts as a tag, so prose like
// "a < b and c > d" survives intact.
private val htmlTagRegex = Regex("</?[a-zA-Z!][^>]*>")

private val namedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
)
private val entityRegex = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")

// Bare links: an address read aloud character by character is pure noise.
private val urlRegex = Regex("(?:https?|ftp)://\\S+|\\bwww\\.\\S+", RegexOption.IGNORE_CASE)

private val whitespaceRunRegex = Regex("\\s+")

/**
 * Reduces page text to what is worth hearing. The YouTube caption path hands
 * TTS a whole HTML document (DualCaptionProcessor.convertToHtml, kept as HTML
 * for parity with Android, where the AI pipeline consumes it too), and ordinary
 * pages leak the occasional inline tag, entity escape, or bare URL — all of
 * which the speech engine happily reads out as words.
 */
fun stripNonSpokenContent(text: String): String {
    var out = scriptElementRegex.replace(text, " ")
    out = styleElementRegex.replace(out, " ")
    out = commentRegex.replace(out, " ")
    // A space, not "": <br> and block tags are word boundaries, and running
    // "one<br>two" together into "onetwo" would be worse than a stray space.
    out = htmlTagRegex.replace(out, " ")
    out = decodeHtmlEntities(out)
    // After decoding, so an &amp;-escaped query string is taken as one URL.
    out = urlRegex.replace(out, " ")
    return whitespaceRunRegex.replace(out, " ").trim()
}

private fun decodeHtmlEntities(text: String): String {
    if (!text.contains('&')) return text
    return entityRegex.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.let(::codePointToString) ?: match.value

            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let(::codePointToString) ?: match.value

            else -> namedEntities[body.lowercase()] ?: match.value
        }
    }
}

/** Non-BMP code points are dropped: they are emoji and symbols, not speech. */
private fun codePointToString(code: Int): String =
    if (code in 1..0xFFFF) code.toChar().toString() else " "

fun String.getWordCount(): Int {
    val trimmedInput = trim()
    if (trimmedInput.isEmpty()) return 0

    // CJK sentences: character count approximates spoken length better.
    if (endsWith("。") || endsWith("？") || endsWith("！")) {
        return trimmedInput.length
    }
    val hangulCount = "[가-힣]+".toRegex().findAll(trimmedInput).sumOf { it.value.length }
    if (hangulCount > 3) return hangulCount

    return "\\p{L}+".toRegex().findAll(trimmedInput).count()
}
