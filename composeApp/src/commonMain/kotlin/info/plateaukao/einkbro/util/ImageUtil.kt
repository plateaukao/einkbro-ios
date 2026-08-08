package info.plateaukao.einkbro.util

/**
 * Result of analyzing a start-page background image: the average colors of the
 * outermost pixel rows (used to extend the image into the letterbox areas) and
 * whether the image is predominantly dark (picks the page theme).
 */
data class ImageStats(
    val topColor: String,
    val bottomColor: String,
    val isDark: Boolean,
)

/**
 * Platform image processing for the start-page background (Android
 * BookmarkRenderer does this with BitmapFactory). The iOS actual goes through
 * UIImage, which transparently handles HEIC sources and EXIF rotation.
 */
expect object ImageUtil {
    /**
     * Decode [bytes], downscale so the longest side is at most [maxDimension],
     * and re-encode — PNG stays PNG (transparency, crisp flat graphics),
     * everything else becomes JPEG. Null when the data is not an image.
     */
    fun processBackgroundImage(bytes: ByteArray, maxDimension: Int): ByteArray?

    /** Average edge colors and perceived brightness of an encoded image. */
    fun analyzeImage(bytes: ByteArray): ImageStats?
}
