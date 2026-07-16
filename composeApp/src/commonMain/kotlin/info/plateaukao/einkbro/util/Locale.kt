package info.plateaukao.einkbro.util

/** Minimal java.util.Locale stand-in for TTS/translation settings UI. */
data class Locale(val language: String, val country: String = "") {
    val displayName: String
        get() = DISPLAY_NAMES[language] ?: language

    fun getDisplayName(inLocale: Locale): String = displayName

    override fun toString(): String =
        if (country.isEmpty()) language else "${language}_$country"

    companion object {
        val ENGLISH = Locale("en")
        fun getDefault(): Locale = Locale("en", "US")

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
