package info.plateaukao.einkbro.view.dialog

import android.content.Context
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.setting_dual_caption
import info.plateaukao.einkbro.resources.translation_language
import info.plateaukao.einkbro.util.TranslationLanguage
import info.plateaukao.einkbro.view.EBToast

/**
 * Port of the Android list-picker dialog for translation languages, backed by
 * DialogManager's select-option flow. The app-locale picker remains a stub —
 * its Android implementation builds on a platform-locale list that has no iOS
 * counterpart yet.
 */
class TranslationLanguageDialog(val context: Context) {

    suspend fun show(): TranslationLanguage? =
        pick(AppServices.config.translation.translationLanguage)

    private suspend fun pick(current: TranslationLanguage): TranslationLanguage? {
        val entries = TranslationLanguage.entries
        val picked = AppServices.dialogManager.getSelectedOptionWithString(
            Res.string.translation_language,
            entries.map { it.language },
            current.ordinal,
        ) ?: return null
        return entries.getOrNull(picked)
    }

    /**
     * Second caption language for YouTube (Android's showDualCaptionLocale).
     * Index 0 is "None", which clears the pref and so uninstalls the
     * `dual_caption_shim.js` hook on the next engine.
     */
    suspend fun showDualCaptionLocale() {
        val entries = TranslationLanguage.entries
        val options = mutableListOf(NONE_OPTION).apply {
            addAll(entries.map { it.language })
        }
        val picked = AppServices.dialogManager.getSelectedOptionWithString(
            Res.string.setting_dual_caption,
            options,
            dualCaptionIndex(AppServices.config.tts.dualCaptionLocale),
        ) ?: return

        AppServices.config.tts.dualCaptionLocale =
            if (picked == 0) "" else entries.getOrNull(picked - 1)?.value.orEmpty()
    }

    private fun dualCaptionIndex(locale: String): Int =
        if (locale.isEmpty()) 0
        else TranslationLanguage.entries.indexOfFirst { it.value == locale } + 1

    suspend fun showAppLocale() {
        EBToast.show(context, "would show app locale picker")
    }

    companion object {
        private const val NONE_OPTION = "None"
    }
}
