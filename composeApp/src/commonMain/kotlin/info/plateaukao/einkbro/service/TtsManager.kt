package info.plateaukao.einkbro.service

import info.plateaukao.einkbro.util.Locale

/**
 * Stub of the Android system-TTS wrapper. Only the surface used by the TTS
 * dialogs exists; speaking is a no-op in the catalog.
 */
class TtsManager {

    fun readText(
        text: String,
        onProgress: (index: Int, total: Int, currentContent: String) -> Unit = { _, _, _ -> },
    ) {
        // no system TTS engine in the catalog
    }

    fun stopReading() {}

    fun setSpeechRate(rate: Float) {}

    fun getAvailableLanguages(): List<Locale> = listOf(
        Locale("en", "US"),
        Locale("zh", "TW"),
        Locale("ja", "JP"),
        Locale("ko", "KR"),
        Locale("fr", "FR"),
        Locale("de", "DE"),
    )
}
