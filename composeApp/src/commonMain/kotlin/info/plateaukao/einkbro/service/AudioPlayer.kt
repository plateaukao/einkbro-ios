package info.plateaukao.einkbro.service

/**
 * Plays a chunk of encoded audio bytes (mp3 from OpenAI-TTS or Edge-TTS) and
 * suspends until it finishes (parity Phase L). AVAudioPlayer on iOS. Pause/
 * resume/stop drive the shared player so the TTS dialog controls work the same
 * as the system-TTS path.
 */
expect class AudioPlayer() {
    /** Plays [bytes] and suspends until playback completes or [stop] is called. */
    suspend fun play(bytes: ByteArray)
    fun pause()
    fun resume()
    fun stop()
    fun isPlaying(): Boolean
    /** Playback rate multiplier (1.0 = normal). */
    fun setRate(rate: Float)
}
