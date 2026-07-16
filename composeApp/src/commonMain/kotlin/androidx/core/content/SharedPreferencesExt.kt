package androidx.core.content

import android.content.SharedPreferences

/** Multiplatform stand-in for androidx.core.content.edit. */
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    editor.action()
    if (commit) editor.commit() else editor.apply()
}
