package info.plateaukao.einkbro.util

/** Minimal java.util.Locale stand-in for TTS/translation settings UI. */
data class Locale(val language: String, val country: String = "") {
    /** Language name in the device's own language, like java.util.Locale.displayName. */
    val displayName: String
        get() = platformLanguageDisplayName(language, null) ?: fallbackName()

    fun getDisplayName(inLocale: Locale): String =
        platformLanguageDisplayName(language, inLocale.language) ?: fallbackName()

    private fun fallbackName(): String = DISPLAY_NAMES[language] ?: language

    override fun toString(): String =
        if (country.isEmpty()) language else "${language}_$country"

    companion object {
        val ENGLISH = Locale("en")

        /** The device language — Android read this from java.util.Locale. */
        fun getDefault(): Locale = Locale(platformDefaultLanguage())

        // Only a fallback now: the platform names every language it ships
        // voices for, so this covers previews and codes iOS doesn't know.
        private val DISPLAY_NAMES = mapOf(
            "en" to "English",
            "zh" to "Chinese",
            "ja" to "Japanese",
            "ko" to "Korean",
            "de" to "German",
            "fr" to "French",
            "es" to "Spanish",
            "it" to "Italian",
        )
    }
}
