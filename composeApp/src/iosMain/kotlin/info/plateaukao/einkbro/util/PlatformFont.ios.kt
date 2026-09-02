package info.plateaukao.einkbro.util

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

actual fun loadFontFamily(identity: String, bytes: ByteArray): FontFamily? =
    try {
        FontFamily(Font(identity = identity, data = bytes))
    } catch (e: Throwable) {
        null
    }
