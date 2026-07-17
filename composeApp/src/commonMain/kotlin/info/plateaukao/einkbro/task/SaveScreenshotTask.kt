package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.System
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.sanitizeFileName
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * iOS port of Android's SaveScreenshotTask: Android captured the full page
 * bitmap and inserted it into MediaStore Screenshots; here the engine's
 * snapshot (visible viewport, JPEG bytes) is written to Documents/screenshots
 * and handed to the system share/preview sheet — the sandbox equivalent of
 * Android's ACTION_VIEW on the saved image.
 */
class SaveScreenshotTask(
    private val engine: WebViewEngine,
) {

    /** Returns the saved file path, or null when capture or write failed. */
    suspend fun execute(): String? {
        val url = engine.currentUrl() ?: return null

        val bytes = withContext(Dispatchers.Main) {
            withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
                suspendCancellableCoroutine<ByteArray?> { cont ->
                    engine.captureSnapshot { data ->
                        if (cont.isActive) cont.resume(data)
                    }
                }
            }
        } ?: return null
        if (bytes.isEmpty()) return null

        val name = fileName(url)
        val path = FileStore.writeBytes("screenshots", "$name.jpg", bytes) ?: return null
        FileStore.share(path)
        return path
    }

    /** Android's HelperUnit.fileName: host + timestamp. */
    private fun fileName(url: String): String {
        val host = Uri.parse(url).host ?: "page"
        return sanitizeFileName("${host}_${System.currentTimeMillis()}", "screenshot")
    }

    companion object {
        private const val SNAPSHOT_TIMEOUT_MS = 10_000L
    }
}
