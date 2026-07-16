package android.content

import info.plateaukao.einkbro.util.PrefsStore
import info.plateaukao.einkbro.util.createPrefsStore

/**
 * Multiplatform stand-in for Android's SharedPreferences, so the original
 * preference layer (delegates + sub-configs) ports verbatim. Values are
 * persisted through [PrefsStore] (NSUserDefaults on iOS).
 */
class SharedPreferences(
    private val store: PrefsStore = createPrefsStore(),
) {

    interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    private val listeners = mutableListOf<OnSharedPreferenceChangeListener>()

    fun getBoolean(key: String, defValue: Boolean): Boolean = store.getBoolean(key, defValue)
    fun getInt(key: String, defValue: Int): Int = store.getInt(key, defValue)
    fun getLong(key: String, defValue: Long): Long = store.getLong(key, defValue)
    fun getFloat(key: String, defValue: Float): Float = store.getFloat(key, defValue)
    fun getString(key: String, defValue: String?): String? = store.getString(key, defValue)
    fun getStringSet(key: String, defValues: Set<String>?): MutableSet<String>? =
        store.getStringSet(key, defValues)

    fun contains(key: String): Boolean = store.contains(key)

    fun edit(): Editor = Editor(this)

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged(key: String) {
        listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
    }

    class Editor(private val prefs: SharedPreferences) {
        private val actions = mutableListOf<Pair<String, (PrefsStore) -> Unit>>()
        private var clearAll = false

        fun putBoolean(key: String, value: Boolean): Editor =
            apply { actions.add(key to { it.putBoolean(key, value) }) }

        fun putInt(key: String, value: Int): Editor =
            apply { actions.add(key to { it.putInt(key, value) }) }

        fun putLong(key: String, value: Long): Editor =
            apply { actions.add(key to { it.putLong(key, value) }) }

        fun putFloat(key: String, value: Float): Editor =
            apply { actions.add(key to { it.putFloat(key, value) }) }

        fun putString(key: String, value: String?): Editor =
            apply { actions.add(key to { it.putString(key, value) }) }

        fun putStringSet(key: String, value: Set<String>?): Editor =
            apply { actions.add(key to { it.putStringSet(key, value) }) }

        fun remove(key: String): Editor =
            apply { actions.add(key to { it.remove(key) }) }

        fun clear(): Editor = apply { clearAll = true }

        fun apply() = commitInternal()
        fun commit(): Boolean { commitInternal(); return true }

        private fun commitInternal() {
            // clear() is only used by tests/backup flows; NSUserDefaults has no
            // cheap clear-all, so it is applied per pending key instead.
            actions.forEach { (_, action) -> action(prefs.store) }
            actions.map { it.first }.distinct().forEach { prefs.notifyChanged(it) }
            actions.clear()
            clearAll = false
        }
    }
}
