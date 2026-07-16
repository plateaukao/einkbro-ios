package info.plateaukao.einkbro.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object FileStore {

    private fun documentsDir(): String? {
        val url = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        return url?.path
    }

    actual fun dirPath(subDir: String): String? {
        val documents = documentsDir() ?: return null
        val dir = "$documents/$subDir"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null,
        )
        return dir
    }

    actual fun writeBytes(subDir: String, fileName: String, bytes: ByteArray): String? {
        val dir = dirPath(subDir) ?: return null
        val path = "$dir/$fileName"
        val data: NSData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(
                bytes = if (bytes.isEmpty()) null else pinned.addressOf(0),
                length = bytes.size.toULong(),
            )
        }
        return if (data.writeToFile(path, atomically = true)) path else null
    }

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    actual fun share(path: String) {
        val url = NSURL.fileURLWithPath(path)
        val controller = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
