package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.TranslateRepository
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.service.TtsManager
import info.plateaukao.einkbro.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Real port of the Android TtsViewModel's system-TTS path: an article queue
 * read sequentially through [TtsManager], with progress/pause/stop state for
 * the TTS dialog. GPT / Edge-TTS engines fall back to system TTS for now.
 */
class TtsViewModel(
    private val config: ConfigManager = AppServices.config,
    private val ttsManager: TtsManager = TtsManager(),
) : ViewModel() {

    private val translateRepository by lazy { TranslateRepository() }

    private val articlesToBeRead: MutableList<String> = mutableListOf()

    private val _readProgress = MutableStateFlow(ReadProgress(0, 0, 0))
    val readProgress: StateFlow<ReadProgress> get() = _readProgress

    private val _readingState = MutableStateFlow(TtsReadingState.IDLE)
    val readingState: StateFlow<TtsReadingState> get() = _readingState

    private val _showCurrentText = MutableStateFlow(config.tts.ttsShowCurrentText)
    val showCurrentText: StateFlow<Boolean> get() = _showCurrentText

    private val _currentReadingContent = MutableStateFlow("")
    val currentReadingContent: StateFlow<String> get() = _currentReadingContent

    fun readArticle(text: String, title: String = "") {
        articlesToBeRead.add(text)
        if (isReading()) {
            updateReadProgress()
            return
        }
        _readingState.value = TtsReadingState.PREPARING

        viewModelScope.launch {
            while (articlesToBeRead.isNotEmpty()) {
                val article = articlesToBeRead.removeAt(0)
                if (article.isEmpty()) continue
                _readingState.value = TtsReadingState.PLAYING
                ttsManager.readText(article) { index, total, currentContent ->
                    updateReadProgress(index, total, currentContent)
                }
            }
            _currentReadingContent.value = ""
            _readProgress.value = ReadProgress(0, 0, 0)
            _readingState.value = TtsReadingState.IDLE
        }
    }

    private fun updateReadProgress(
        index: Int = _readProgress.value.index,
        total: Int = _readProgress.value.total,
        text: String? = null,
    ) {
        _readProgress.value = ReadProgress(index, total, articlesToBeRead.size)
        text?.let { maybeInsertTranslationText(it) }
    }

    private fun maybeInsertTranslationText(text: String) {
        if (!config.tts.ttsShowTextTranslation) {
            _currentReadingContent.value = text
            return
        }
        _currentReadingContent.value = text
        viewModelScope.launch {
            val translated = translateRepository.gTranslateWithApi(
                text, config.translation.translationLanguage.value
            )
            if (!translated.isNullOrBlank() &&
                _currentReadingContent.value.substringBefore(TRANSLATION_SEPARATOR) == text
            ) {
                _currentReadingContent.value = "$text$TRANSLATION_SEPARATOR$translated"
            }
        }
    }

    fun setSpeechRate(rate: Float) = ttsManager.setSpeechRate(rate)

    fun pauseOrResume() {
        when (_readingState.value) {
            TtsReadingState.PLAYING -> {
                ttsManager.pause()
                _readingState.value = TtsReadingState.PAUSED
            }
            TtsReadingState.PAUSED -> {
                ttsManager.resume()
                _readingState.value = TtsReadingState.PLAYING
            }
            else -> Unit
        }
    }

    fun hasNextArticle(): Boolean = articlesToBeRead.isNotEmpty()

    /** Skips the article being read; the read loop proceeds with the queue. */
    fun nextArticle() {
        if (isReading()) ttsManager.stopReading()
    }

    fun reset() {
        articlesToBeRead.clear()
        ttsManager.stopReading()
        _currentReadingContent.value = ""
        _readProgress.value = ReadProgress(0, 0, 0)
        _readingState.value = TtsReadingState.IDLE
    }

    fun stop() = reset()

    fun isReading(): Boolean = _readingState.value != TtsReadingState.IDLE

    fun toggleShowCurrentText() {
        config.tts.ttsShowCurrentText = !config.tts.ttsShowCurrentText
        _showCurrentText.value = config.tts.ttsShowCurrentText
    }

    fun toggleShowTranslation() {
        config.tts.ttsShowTextTranslation = !config.tts.ttsShowTextTranslation
        val current = _currentReadingContent.value.substringBefore(TRANSLATION_SEPARATOR)
        if (config.tts.ttsShowTextTranslation && current.isNotBlank()) {
            maybeInsertTranslationText(current)
        } else {
            _currentReadingContent.value = current
        }
    }

    fun getAvailableLanguages(): List<Locale> = ttsManager.getAvailableLanguages()

    override fun onCleared() {
        reset()
        super.onCleared()
    }

    companion object {
        private const val TRANSLATION_SEPARATOR = "\n---\n"
    }
}
