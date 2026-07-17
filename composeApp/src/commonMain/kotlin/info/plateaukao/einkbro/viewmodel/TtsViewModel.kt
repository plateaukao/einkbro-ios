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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

    // Fetch-ahead pipeline (Android's readByEngine): the fetch job pushes
    // sentence audio through this channel while the play loop consumes it, so
    // the next sentence is already downloaded when the current one ends.
    private var byteArrayChannel: Channel<ChannelData>? = null

    private val articlesToBeRead: MutableList<String> = mutableListOf()

    private val _readProgress = MutableStateFlow(ReadProgress(0, 0, 0))
    val readProgress: StateFlow<ReadProgress> get() = _readProgress

    private val _readingState = MutableStateFlow(TtsReadingState.IDLE)
    val readingState: StateFlow<TtsReadingState> get() = _readingState

    private val _showCurrentText = MutableStateFlow(config.tts.ttsShowCurrentText)
    val showCurrentText: StateFlow<Boolean> get() = _showCurrentText

    private val _currentReadingContent = MutableStateFlow("")
    val currentReadingContent: StateFlow<String> get() = _currentReadingContent

    private var pageTitle = ""

    init {
        // Lock-screen / Control-Center transport + now-playing (Android's
        // TtsNotificationManager equivalent).
        info.plateaukao.einkbro.service.MediaSession.configure(
            onPlayPause = ::pauseOrResume,
            onNext = ::nextArticle,
            onStop = ::reset,
        )
        viewModelScope.launch {
            _readingState.collect { state ->
                when (state) {
                    TtsReadingState.IDLE ->
                        info.plateaukao.einkbro.service.MediaSession.clear()
                    else -> info.plateaukao.einkbro.service.MediaSession.update(
                        title = pageTitle.ifBlank { "EinkBro" },
                        isPlaying = state == TtsReadingState.PLAYING,
                    )
                }
            }
        }
    }

    fun readArticle(text: String, title: String = "") {
        if (title.isNotBlank()) pageTitle = title
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
                when (config.tts.ttsType) {
                    TtsType.SYSTEM -> {
                        _readingState.value = TtsReadingState.PLAYING
                        ttsManager.readText(article) { index, total, currentContent ->
                            updateReadProgress(index, total, currentContent)
                        }
                    }

                    // Stays PREPARING until the first sentence's audio arrives.
                    TtsType.GPT, TtsType.ETTS -> readArticleOverNetwork(article)
                }
            }
            _currentReadingContent.value = ""
            _readProgress.value = ReadProgress(0, 0, 0)
            _readingState.value = TtsReadingState.IDLE
        }
    }

    /**
     * GPT / Edge-TTS: mirror of Android's readByEngine. A fetch job downloads
     * sentence mp3s ahead of playback and hands them over via [byteArrayChannel];
     * the play loop below consumes them back-to-back, so there is no network
     * gap between sentences. Speed is baked into the request (OpenAI `speed`,
     * Edge SSML rate) so the player runs at 1x.
     */
    private suspend fun readArticleOverNetwork(article: String) {
        byteArrayChannel?.cancel()
        val channel = Channel<ChannelData>(1)
        byteArrayChannel = channel
        val chunks = processedTextToChunks(article)

        // Android uses Dispatchers.IO; that alias is JVM-only, and the fetches
        // are suspending Ktor calls anyway, so Default serves the same purpose.
        val fetchJob = viewModelScope.launch(Dispatchers.Default) {
            for ((index, chunk) in chunks.withIndex()) {
                if (byteArrayChannel != channel) return@launch
                val bytes = when (config.tts.ttsType) {
                    TtsType.GPT -> openAiRepository.tts(chunk)
                    TtsType.ETTS -> eTts.tts(config.tts.ettsVoice, config.tts.ttsSpeedValue, chunk)
                    else -> null
                }
                if (byteArrayChannel != channel) return@launch
                // send suspends until the play loop takes the previous chunk;
                // throws (ending the job) once the channel is cancelled.
                runCatching { channel.send(ChannelData(bytes, chunk, index)) }
                    .onFailure { return@launch }
            }
        }

        while (byteArrayChannel == channel) {
            val data = runCatching { channel.receive() }.getOrNull() ?: break
            updateReadProgress(data.chunkIndex + 1, chunks.size, data.text)
            if (data.byteArray != null && data.byteArray.isNotEmpty()) {
                if (_readingState.value == TtsReadingState.PREPARING) {
                    _readingState.value = TtsReadingState.PLAYING
                }
                activeNetwork = true
                audioPlayer.play(data.byteArray)
                activeNetwork = false
            }
            if (data.chunkIndex == chunks.size - 1) break
        }

        fetchJob.cancel()
        if (byteArrayChannel == channel) {
            channel.cancel()
            byteArrayChannel = null
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
        stopCurrentEngine()
    }

    fun reset() {
        articlesToBeRead.clear()
        stopCurrentEngine()
        _currentReadingContent.value = ""
        _readProgress.value = ReadProgress(0, 0, 0)
        _readingState.value = TtsReadingState.IDLE
    }

    // Android's stop(): tear down the fetch/play pipeline and silence whichever
    // engine is active; the read loop then moves on (or ends).
    private fun stopCurrentEngine() {
        byteArrayChannel?.cancel()
        byteArrayChannel = null
        audioPlayer.stop()
        ttsManager.stopReading()
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

private class ChannelData(val byteArray: ByteArray?, val text: String, val chunkIndex: Int)
