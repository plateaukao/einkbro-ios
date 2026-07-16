package info.plateaukao.einkbro.view

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import info.plateaukao.einkbro.util.blockingString
import org.jetbrains.compose.resources.StringResource

/**
 * Toast stand-in: publishes the message as observable state; the catalog host
 * renders it as a transient overlay.
 */
object EBToast {
    val current = mutableStateOf<String?>(null)

    fun show(context: Context?, stringResId: StringResource) {
        current.value = blockingString(stringResId)
    }

    fun show(context: Context?, text: String?) {
        current.value = text
    }

    fun showShort(context: Context?, stringResId: StringResource) = show(context, stringResId)

    fun showShort(context: Context?, text: String?) = show(context, text)
}
