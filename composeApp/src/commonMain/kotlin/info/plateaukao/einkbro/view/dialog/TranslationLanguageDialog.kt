package info.plateaukao.einkbro.view.dialog

import android.content.Context
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.translation_language
import info.plateaukao.einkbro.util.TranslationLanguage
import info.plateaukao.einkbro.view.EBToast

/**
 * Port of the Android list-picker dialog for translation languages, backed by
 * DialogManager's select-option flow. The locale pickers (dual caption / app
 * locale) remain stubs — their Android implementations build on
 * platform-locale lists that have no iOS counterpart yet.
 */
class TranslationLanguageDialog(val context: Context) {

    suspend fun show(): TranslationLanguage? =
        pick(AppServices.config.translation.translationLanguage)

    suspend fun showPapagoSourceLanguage(): TranslationLanguage? =
        pick(AppServices.config.translation.sourceLanguage)

    private suspend fun pick(current: TranslationLanguage): TranslationLanguage? {
        val entries = TranslationLanguage.entries
        val picked = AppServices.dialogManager.getSelectedOptionWithString(
            Res.string.translation_language,
            entries.map { it.language },
            current.ordinal,
        ) ?: return null
        return entries.getOrNull(picked)
    }

    suspend fun showDualCaptionLocale() {
        EBToast.show(context, "would show dual caption locale picker")
    }

    suspend fun showAppLocale() {
        EBToast.show(context, "would show app locale picker")
    }
}
