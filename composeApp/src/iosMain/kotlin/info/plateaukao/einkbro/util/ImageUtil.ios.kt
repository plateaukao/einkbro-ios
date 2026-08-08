package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class)
actual object ImageUtil {

    actual fun processBackgroundImage(bytes: ByteArray, maxDimension: Int): ByteArray? {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
        // UIImage.size is in points with the EXIF orientation applied; drawing
        // through UIKit below bakes both scale and orientation into the output.
        val (pixelWidth, pixelHeight) = image.size.useContents {
            (width * image.scale) to (height * image.scale)
        }
        if (pixelWidth < 1.0 || pixelHeight < 1.0) return null
        val factor = (maxDimension / maxOf(pixelWidth, pixelHeight)).coerceAtMost(1.0)
        val targetWidth = (pixelWidth * factor).coerceAtLeast(1.0)
        val targetHeight = (pixelHeight * factor).coerceAtLeast(1.0)

        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(targetWidth, targetHeight), false, 1.0
        )
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        val scaled = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        if (scaled == null) return null

        // keep PNG sources as PNG (transparency, crisp flat graphics);
        // everything else re-encodes to JPEG
        val data = if (isPng(bytes)) {
            UIImagePNGRepresentation(scaled)
        } else {
            UIImageJPEGRepresentation(scaled, 0.8)
        }
        return data?.toByteArray()
    }

    actual fun analyzeImage(bytes: ByteArray): ImageStats? {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
        val cgImage = image.CGImage ?: return null

        // color averaging doesn't need resolution; a tiny RGBA raster is
        // enough, composited over white so transparency counts as white
        val sample = 32
        val bytesPerRow = sample * 4
        val buffer = ByteArray(sample * bytesPerRow)
        buffer.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                sample.toULong(),
                sample.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            )
            if (context == null) {
                CGColorSpaceRelease(colorSpace)
                return null
            }
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(
                context, CGRectMake(0.0, 0.0, sample.toDouble(), sample.toDouble())
            )
            CGContextDrawImage(
                context, CGRectMake(0.0, 0.0, sample.toDouble(), sample.toDouble()), cgImage
            )
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }

        // buffer row 0 is the top of the drawn image (CGBitmapContext memory
        // starts at the top-left; CGContextDrawImage lands the image upright)
        var luma = 0.0
        for (index in 0 until sample * sample) {
            val offset = index * 4
            luma += 0.299 * (buffer[offset].toInt() and 0xFF) +
                0.587 * (buffer[offset + 1].toInt() and 0xFF) +
                0.114 * (buffer[offset + 2].toInt() and 0xFF)
        }
        return ImageStats(
            topColor = averageRowColor(buffer, bytesPerRow, row = 0),
            bottomColor = averageRowColor(buffer, bytesPerRow, row = sample - 1),
            isDark = luma / (sample * sample) < 128,
        )
    }

    private fun averageRowColor(buffer: ByteArray, bytesPerRow: Int, row: Int): String {
        val pixels = bytesPerRow / 4
        var r = 0.0
        var g = 0.0
        var b = 0.0
        for (x in 0 until pixels) {
            val offset = row * bytesPerRow + x * 4
            r += (buffer[offset].toInt() and 0xFF)
            g += (buffer[offset + 1].toInt() and 0xFF)
            b += (buffer[offset + 2].toInt() and 0xFF)
        }
        return hexColor((r / pixels).toInt(), (g / pixels).toInt(), (b / pixels).toInt())
    }

    private fun hexColor(r: Int, g: Int, b: Int): String {
        fun component(value: Int) = value.coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${component(r)}${component(g)}${component(b)}"
    }

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData()
    else usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
