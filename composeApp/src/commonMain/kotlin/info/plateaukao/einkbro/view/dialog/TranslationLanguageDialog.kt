package info.plateaukao.einkbro.view.dialog

import android.content.Context
import info.plateaukao.einkbro.util.TranslationLanguage
import info.plateaukao.einkbro.view.EBToast

/**
 * Stub of the Android list-picker dialog for translation languages. In the
 * catalog it cannot present a modal list, so it announces itself and resolves
 * to null (= cancelled), which callers already handle.
 */
class TranslationLanguageDialog(val context: Context) {

    suspend fun show(): TranslationLanguage? {
        EBToast.show(context, "would show translation language picker")
        return null
    }

    suspend fun showPapagoSourceLanguage(): TranslationLanguage? {
        EBToast.show(context, "would show source language picker")
        return null
    }

    suspend fun showDualCaptionLocale() {
        EBToast.show(context, "would show dual caption locale picker")
    }

    suspend fun showAppLocale() {
        EBToast.show(context, "would show app locale picker")
    }
}
