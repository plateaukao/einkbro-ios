package info.plateaukao.einkbro.service

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSData
import platform.Foundation.create
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class AudioPlayer actual constructor() {

    private var player: AVAudioPlayer? = null
    private var delegate: PlayerDelegate? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var rate: Float = 1f
    private var stopped = false

    init {
        // Playback category so audio sounds even with the ringer silent.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
    }

    actual suspend fun play(bytes: ByteArray): Unit = suspendCancellableCoroutine { cont ->
        stopped = false
        val data = bytes.toNSData()
        val newPlayer = AVAudioPlayer(data = data, error = null)
        if (newPlayer == null) {
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val newDelegate = PlayerDelegate { finishTurn() }
        newPlayer.setDelegate(newDelegate)
        newPlayer.enableRate = true
        newPlayer.rate = rate
        newPlayer.prepareToPlay()

        player = newPlayer
        delegate = newDelegate
        continuation = cont

        cont.invokeOnCancellation { stop() }

        // The shared session is configured for Playback in init; play() activates
        // it implicitly, which is enough for foreground audio.
        if (!newPlayer.play()) {
            finishTurn()
        }
    }

    private fun finishTurn() {
        val cont = continuation
        continuation = null
        player = null
        delegate = null
        cont?.let { if (it.isActive) it.resume(Unit) }
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun resume() {
        player?.play()
    }

    actual fun stop() {
        stopped = true
        player?.stop()
        finishTurn()
    }

    actual fun isPlaying(): Boolean = player?.isPlaying() ?: false

    actual fun setRate(rate: Float) {
        this.rate = rate
        player?.rate = rate
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private class PlayerDelegate(
    private val onFinished: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {

    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinished()
    }
}
