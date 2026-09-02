package info.plateaukao.einkbro.util

import androidx.compose.ui.text.font.FontFamily

/**
 * Builds a Compose [FontFamily] from raw font-file bytes (ttf/otf), for
 * previewing a user-supplied font in the font browser — the counterpart of
 * Android's Typeface.Builder(fd).build(). Null when the bytes aren't a font
 * the platform can parse.
 */
expect fun loadFontFamily(identity: String, bytes: ByteArray): FontFamily?
