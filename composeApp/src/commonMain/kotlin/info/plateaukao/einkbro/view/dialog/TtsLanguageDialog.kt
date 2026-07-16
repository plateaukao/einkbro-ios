package info.plateaukao.einkbro.view.dialog

import android.content.Context
import info.plateaukao.einkbro.util.Locale
import info.plateaukao.einkbro.view.EBToast

/**
 * Stub of the Android single-choice dialog for the system TTS locale.
 */
class TtsLanguageDialog(val context: Context) {

    fun show(locales: List<Locale>) {
        EBToast.show(
            context,
            "would show TTS language picker (${locales.size} locales available)"
        )
    }
}
