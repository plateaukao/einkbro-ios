package info.plateaukao.einkbro.util

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes encoded image bytes (PNG/JPEG/ICO...) to an ImageBitmap; null on failure. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
