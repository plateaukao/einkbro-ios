package info.plateaukao.einkbro.service

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.darwin.NSObject

/**
 * AVSpeechSynthesizer-backed system TTS. All chunks are queued up-front
 * (AVSpeechSynthesizer keeps its own utterance queue, like Android's
 * QUEUE_ADD); only didFinishSpeechUtterance is implemented — the delegate's
 * other same-signature callbacks conflict in Kotlin/Native — so "current
 * chunk" advances when the previous one completes.
 */
actual class TtsManager actual constructor() {

    private val synthesizer = AVSpeechSynthesizer()
    private val delegate = SpeechDelegate(this)

    private var chunks: List<String> = emptyList()
    private var finishedCount = 0
    private var onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    private var continuation: kotlinx.coroutines.CancellableContinuation<Unit>? = null
    private var speechRate: Float = 1f
    private var isStopped = false

    init {
        synthesizer.delegate = delegate
    }

    actual fun setSpeechRate(rate: Float) {
        speechRate = rate
    }

    actual fun getAvailableLanguages(): List<Locale> =
        AVSpeechSynthesisVoice.speechVoices()
            .mapNotNull { (it as? AVSpeechSynthesisVoice)?.language?.substringBefore('-') }
            .distinct()
            .map { Locale(it) }

    actual suspend fun readText(
        text: String,
        onProgress: (Int, Int, String) -> Unit,
    ) = suspendCancellableCoroutine { cont ->
        isStopped = false
        chunks = processedTextToChunks(text)
        if (chunks.isEmpty()) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        finishedCount = 0
        this.onProgress = onProgress
        continuation = cont
        cont.invokeOnCancellation { stopReading() }

        val voice = voiceForConfiguredLanguage()
        val rate = (AVSpeechUtteranceDefaultSpeechRate *
            (AppServices.config.tts.ttsSpeedValue / 100f) * speechRate)
            .coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)

        onProgress(1, chunks.size, chunks[0])
        chunks.forEach { chunk ->
            val utterance = AVSpeechUtterance.speechUtteranceWithString(chunk)
            voice?.let { utterance.voice = it }
            utterance.rate = rate
            synthesizer.speakUtterance(utterance)
        }
    }

    private fun voiceForConfiguredLanguage(): AVSpeechSynthesisVoice? {
        val language = AppServices.config.tts.ttsLocale.language
        val bcp47 = when (language) {
            "en" -> "en-US"
            "zh" -> "zh-TW"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "it" -> "it-IT"
            else -> language
        }
        return AVSpeechSynthesisVoice.voiceWithLanguage(bcp47)
            ?: AVSpeechSynthesisVoice.voiceWithLanguage(language)
    }

    internal fun onUtteranceFinished() {
        if (isStopped) return
        finishedCount++
        if (finishedCount >= chunks.size) {
            finish()
        } else {
            onProgress(finishedCount + 1, chunks.size, chunks[finishedCount])
        }
    }

    private fun finish() {
        val cont = continuation
        continuation = null
        cont?.resume(Unit)
    }

    actual fun isSpeaking(): Boolean = synthesizer.isSpeaking()

    actual fun pause() {
        synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    actual fun resume() {
        synthesizer.continueSpeaking()
    }

    actual fun stopReading() {
        isStopped = true
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        finish()
    }
}

private class SpeechDelegate(
    private val manager: TtsManager,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance,
    ) {
        manager.onUtteranceFinished()
    }
}
