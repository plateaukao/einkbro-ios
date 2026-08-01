package info.plateaukao.einkbro.util

/**
 * The bits of java.util.Locale that need the OS: the device language, and
 * human-readable language names. [Locale] is a plain data holder and delegates
 * here, so it keeps behaving like the Android original it was ported from.
 */

/** Primary subtag of the device language, e.g. "zh" — Android's Locale.getDefault().language. */
expect fun platformDefaultLanguage(): String

/**
 * Name of [languageCode] written in [inLanguageCode] (the device language when
 * null), e.g. ("ko", null) -> "韓文" on a zh-TW phone. Null when the platform
 * has no name for the code.
 */
expect fun platformLanguageDisplayName(languageCode: String, inLanguageCode: String?): String?
