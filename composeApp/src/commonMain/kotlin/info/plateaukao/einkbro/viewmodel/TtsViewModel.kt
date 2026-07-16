package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.service.TtsManager
import info.plateaukao.einkbro.util.Locale
import info.plateaukao.einkbro.view.EBToast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stub of the Android TtsViewModel (which drives real TTS engines and a media
 * player). Exposes the same observable state, pre-seeded to look mid-reading:
 * sentence 3 of 12, one queued article, a sample sentence on screen.
 */
class TtsViewModel(
    private val config: ConfigManager = AppServices.config,
    private val ttsManager: TtsManager = TtsManager(),
) : ViewModel() {

    private val articlesToBeRead: MutableList<String> = mutableListOf(SAMPLE_QUEUED_ARTICLE)

    private val _readProgress = MutableStateFlow(ReadProgress(3, 12, articlesToBeRead.size))
    val readProgress: StateFlow<ReadProgress> get() = _readProgress

    private val _readingState = MutableStateFlow(TtsReadingState.PLAYING)
    val readingState: StateFlow<TtsReadingState> get() = _readingState

    private val _showCurrentText = MutableStateFlow(config.tts.ttsShowCurrentText)
    val showCurrentText: StateFlow<Boolean> get() = _showCurrentText

    private val _currentReadingContent = MutableStateFlow(SAMPLE_READING_SENTENCE)
    val currentReadingContent: StateFlow<String> get() = _currentReadingContent

    fun readArticle(text: String, title: String = "") {
        articlesToBeRead.add(text)
        _readingState.value = TtsReadingState.PLAYING
        _readProgress.value = ReadProgress(1, 12, articlesToBeRead.size)
        _currentReadingContent.value = text.take(200).ifBlank { SAMPLE_READING_SENTENCE }
    }

    fun setSpeechRate(rate: Float) = ttsManager.setSpeechRate(rate)

    fun pauseOrResume() {
        _readingState.value = when (_readingState.value) {
            TtsReadingState.PLAYING -> TtsReadingState.PAUSED
            TtsReadingState.PAUSED -> TtsReadingState.PLAYING
            else -> _readingState.value
        }
    }

    fun hasNextArticle(): Boolean = articlesToBeRead.isNotEmpty()

    fun nextArticle() {
        if (articlesToBeRead.isNotEmpty()) {
            articlesToBeRead.removeAt(0)
        }
        _readProgress.value = ReadProgress(1, 12, articlesToBeRead.size)
        EBToast.show(AppServices.context, "would skip to the next queued article")
    }

    fun reset() {
        articlesToBeRead.clear()
        _currentReadingContent.value = ""
        _readProgress.value = ReadProgress(0, 0, 0)
        _readingState.value = TtsReadingState.IDLE
    }

    fun stop() {
        ttsManager.stopReading()
        _readingState.value = TtsReadingState.IDLE
    }

    fun isReading(): Boolean = _readingState.value != TtsReadingState.IDLE

    fun toggleShowCurrentText() {
        config.tts.ttsShowCurrentText = !config.tts.ttsShowCurrentText
        _showCurrentText.value = config.tts.ttsShowCurrentText
    }

    fun toggleShowTranslation() {
        config.tts.ttsShowTextTranslation = !config.tts.ttsShowTextTranslation
        _currentReadingContent.value =
            if (config.tts.ttsShowTextTranslation) {
                "$SAMPLE_READING_SENTENCE$TRANSLATION_SEPARATOR$SAMPLE_READING_TRANSLATION"
            } else {
                _currentReadingContent.value.substringBefore(TRANSLATION_SEPARATOR)
            }
    }

    fun getAvailableLanguages(): List<Locale> = ttsManager.getAvailableLanguages()

    companion object {
        private const val TRANSLATION_SEPARATOR = "\n---\n"
        const val SAMPLE_READING_SENTENCE =
            "E-ink displays reflect ambient light instead of emitting their own, " +
                    "which is why reading on them feels closer to paper."
        const val SAMPLE_READING_TRANSLATION =
            "電子紙螢幕反射環境光而非自行發光，因此在上面閱讀的感覺更接近紙張。"
        const val SAMPLE_QUEUED_ARTICLE =
            "A second article waiting in the read list."
    }
}
