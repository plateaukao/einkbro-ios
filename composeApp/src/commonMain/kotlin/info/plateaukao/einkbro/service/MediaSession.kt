package info.plateaukao.einkbro.service

/**
 * Lock-screen / Control-Center now-playing metadata and transport commands for
 * TTS and AI audio playback. On iOS this bridges MPNowPlayingInfoCenter and
 * MPRemoteCommandCenter; other platforms may no-op.
 */
expect object MediaSession {
    /** Registers transport handlers (called once). play/pause toggles reading. */
    fun configure(onPlayPause: () -> Unit, onNext: () -> Unit, onStop: () -> Unit)

    /** Publishes the current item + playback state to the system UI. */
    fun update(title: String, isPlaying: Boolean)

    /** Removes now-playing info (reading stopped). */
    fun clear()
}
