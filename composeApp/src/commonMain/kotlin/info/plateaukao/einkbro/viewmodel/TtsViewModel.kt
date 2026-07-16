package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.data.remote.TranslateRepository
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.service.AudioPlayer
import info.plateaukao.einkbro.service.TtsManager
import info.plateaukao.einkbro.service.processedTextToChunks
import info.plateaukao.einkbro.tts.ETts
import info.plateaukao.einkbro.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Port of the Android TtsViewModel: an article queue read sequentially, with
 * progress/pause/stop state for the TTS dialog. Three engines (parity Phase L):
 * SYSTEM (AVSpeechSynthesizer via [TtsManager]), GPT (OpenAI TTS) and ETTS
 * (Edge neural TTS) — the network engines fetch mp3 per chunk and play it
 * through the shared [AudioPlayer].
 */
class TtsViewModel(
    private val config: ConfigManager = AppServices.config,
    private val ttsManager: TtsManager = TtsManager(),
) : ViewModel() {

    private val translateRepository by lazy { TranslateRepository() }
    private val openAiRepository by lazy { OpenAiRepository() }
    private val eTts by lazy { ETts() }
    private val audioPlayer by lazy { AudioPlayer() }

    // True while a network engine (GPT/ETTS) drives the AudioPlayer, so the
    // transport controls route to it instead of the system synthesizer.
    private var activeNetwork = false
    private var skipArticle = false

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
                when (config.tts.ttsType) {
                    TtsType.SYSTEM -> ttsManager.readText(article) { index, total, currentContent ->
                        updateReadProgress(index, total, currentContent)
                    }

                    TtsType.GPT, TtsType.ETTS -> readArticleOverNetwork(article)
                }
            }
            _currentReadingContent.value = ""
            _readProgress.value = ReadProgress(0, 0, 0)
            _readingState.value = TtsReadingState.IDLE
        }
    }

    /**
     * GPT / Edge-TTS: fetch the mp3 for each chunk and play it through the
     * AudioPlayer, awaiting each before the next. Speed is baked into the
     * request (OpenAI `speed`, Edge SSML rate) so the player runs at 1x.
     */
    private suspend fun readArticleOverNetwork(article: String) {
        val chunks = processedTextToChunks(article)
        skipArticle = false
        for ((index, chunk) in chunks.withIndex()) {
            if (skipArticle || _readingState.value == TtsReadingState.IDLE) break
            updateReadProgress(index + 1, chunks.size, chunk)
            val bytes = when (config.tts.ttsType) {
                TtsType.GPT -> openAiRepository.tts(chunk)
                TtsType.ETTS -> eTts.tts(config.tts.ettsVoice, config.tts.ttsSpeedValue, chunk)
                else -> null
            }
            if (skipArticle || _readingState.value == TtsReadingState.IDLE) break
            if (bytes != null && bytes.isNotEmpty()) {
                activeNetwork = true
                audioPlayer.play(bytes)
                activeNetwork = false
            }
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
                if (activeNetwork) audioPlayer.pause() else ttsManager.pause()
                _readingState.value = TtsReadingState.PAUSED
            }
            TtsReadingState.PAUSED -> {
                if (activeNetwork) audioPlayer.resume() else ttsManager.resume()
                _readingState.value = TtsReadingState.PLAYING
            }
            else -> Unit
        }
    }

    fun hasNextArticle(): Boolean = articlesToBeRead.isNotEmpty()

    /** Skips the article being read; the read loop proceeds with the queue. */
    fun nextArticle() {
        if (!isReading()) return
        skipArticle = true
        if (activeNetwork) audioPlayer.stop() else ttsManager.stopReading()
    }

    fun reset() {
        articlesToBeRead.clear()
        skipArticle = true
        audioPlayer.stop()
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
