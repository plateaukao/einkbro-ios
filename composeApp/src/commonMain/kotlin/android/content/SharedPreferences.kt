package android.content

/**
 * In-memory multiplatform stand-in for Android's SharedPreferences, so the
 * original preference layer (delegates + sub-configs) ports verbatim.
 * Values live only for the app session — good enough for UI verification.
 */
@Suppress("UNCHECKED_CAST")
class SharedPreferences {

    interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<OnSharedPreferenceChangeListener>()

    fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    fun getStringSet(key: String, defValues: Set<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()

    fun contains(key: String): Boolean = key in values
    fun getAll(): Map<String, Any?> = values.toMap()

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
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        fun putBoolean(key: String, value: Boolean): Editor = apply { pending[key] = value }
        fun putInt(key: String, value: Int): Editor = apply { pending[key] = value }
        fun putLong(key: String, value: Long): Editor = apply { pending[key] = value }
        fun putFloat(key: String, value: Float): Editor = apply { pending[key] = value }
        fun putString(key: String, value: String?): Editor = apply { pending[key] = value }
        fun putStringSet(key: String, value: Set<String>?): Editor = apply { pending[key] = value }
        fun remove(key: String): Editor = apply { removals.add(key) }
        fun clear(): Editor = apply { clearAll = true }

        fun apply() = commitInternal()
        fun commit(): Boolean { commitInternal(); return true }

        private fun commitInternal() {
            if (clearAll) prefs.values.clear()
            removals.forEach { prefs.values.remove(it) }
            pending.forEach { (k, v) -> if (v == null) prefs.values.remove(k) else prefs.values[k] = v }
            (removals + pending.keys).forEach { prefs.notifyChanged(it) }
            pending.clear(); removals.clear(); clearAll = false
        }
    }
}
