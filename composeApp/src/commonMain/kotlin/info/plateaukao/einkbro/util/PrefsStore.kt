package info.plateaukao.einkbro.util

/**
 * Storage backend for the android.content.SharedPreferences shim.
 * The iOS actual persists to NSUserDefaults so settings survive relaunch.
 */
interface PrefsStore {
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): MutableSet<String>?

    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun putString(key: String, value: String?)
    fun putStringSet(key: String, value: Set<String>?)

    fun remove(key: String)
    fun contains(key: String): Boolean
}

expect fun createPrefsStore(): PrefsStore
