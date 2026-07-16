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
    val processedText = text.replace("\\n", " ")
        .replace("\\\"", "")
        .replace("\\t", "")
        .replace("\\", "")
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
