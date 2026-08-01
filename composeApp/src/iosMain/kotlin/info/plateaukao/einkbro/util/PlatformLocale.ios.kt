package info.plateaukao.einkbro.util

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.preferredLanguages

actual fun platformDefaultLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.substringBefore('-')
        ?.substringBefore('_')
        ?.takeIf { it.isNotBlank() }
        ?: "en"

actual fun platformLanguageDisplayName(languageCode: String, inLanguageCode: String?): String? {
    if (languageCode.isBlank()) return null
    val locale = if (inLanguageCode.isNullOrBlank()) {
        NSLocale.currentLocale
    } else {
        NSLocale(localeIdentifier = inLanguageCode)
    }
    // Returns the code back for languages it doesn't know; treat that as "no name".
    return locale.localizedStringForLanguageCode(languageCode)
        ?.takeIf { it.isNotBlank() && it != languageCode }
}
