package info.plateaukao.einkbro.service

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSNumber
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * iOS now-playing bridge. The playback-category audio session is kept active
 * so TTS/AI audio (and the remote controls) keep working while the app is
 * backgrounded — paired with UIBackgroundModes:audio in Info.plist.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object MediaSession {
    private var configured = false

    actual fun configure(onPlayPause: () -> Unit, onNext: () -> Unit, onStop: () -> Unit) {
        if (configured) return
        configured = true
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)

        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.setEnabled(true)
        center.pauseCommand.setEnabled(true)
        center.togglePlayPauseCommand.setEnabled(true)
        center.stopCommand.setEnabled(true)
        center.nextTrackCommand.setEnabled(true)

        fun ok(action: () -> Unit): MPRemoteCommandHandlerStatus {
            action()
            return MPRemoteCommandHandlerStatusSuccess
        }
        center.playCommand.addTargetWithHandler { ok(onPlayPause) }
        center.pauseCommand.addTargetWithHandler { ok(onPlayPause) }
        center.togglePlayPauseCommand.addTargetWithHandler { ok(onPlayPause) }
        center.stopCommand.addTargetWithHandler { ok(onStop) }
        center.nextTrackCommand.addTargetWithHandler { ok(onNext) }
    }

    actual fun update(title: String, isPlaying: Boolean) {
        AVAudioSession.sharedInstance().setActive(true, null)
        val info = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to title,
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(float = if (isPlaying) 1f else 0f),
        )
        MPNowPlayingInfoCenter.defaultCenter().setNowPlayingInfo(info)
    }

    actual fun clear() {
        MPNowPlayingInfoCenter.defaultCenter().setNowPlayingInfo(null)
        AVAudioSession.sharedInstance().setActive(false, null)
    }
}
