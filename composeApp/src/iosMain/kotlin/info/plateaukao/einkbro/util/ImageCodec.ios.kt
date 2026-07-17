package info.plateaukao.einkbro.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    // Skia handles PNG/JPEG/GIF/WebP/BMP but not ICO; favicons are often real
    // ICO containers, so fall back to ImageIO (UIImage) and re-encode as PNG.
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }
        .getOrNull()?.let { return it }
    val nsData = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.convert())
    }
    val pngData = UIImage.imageWithData(nsData)?.let { UIImagePNGRepresentation(it) }
        ?: return null
    return runCatching {
        Image.makeFromEncoded(pngData.toByteArray()).toComposeImageBitmap()
    }.getOrNull()
}
