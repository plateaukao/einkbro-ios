package info.plateaukao.einkbro.util

import platform.Foundation.NSUserDefaults

private class UserDefaultsPrefsStore : PrefsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else defValue

    override fun getInt(key: String, defValue: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else defValue

    override fun getLong(key: String, defValue: Long): Long =
        if (contains(key)) defaults.integerForKey(key) else defValue

    override fun getFloat(key: String, defValue: Float): Float =
        if (contains(key)) defaults.floatForKey(key) else defValue

    override fun getString(key: String, defValue: String?): String? =
        defaults.stringForKey(key) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): MutableSet<String>? =
        (defaults.arrayForKey(key) as? List<String>)?.toMutableSet()
            ?: defValues?.toMutableSet()

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)
    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)
    override fun putLong(key: String, value: Long) = defaults.setInteger(value, key)
    override fun putFloat(key: String, value: Float) = defaults.setFloat(value, key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key)
        else defaults.setObject(value, key)
    }

    override fun putStringSet(key: String, value: Set<String>?) {
        if (value == null) defaults.removeObjectForKey(key)
        else defaults.setObject(value.toList(), key)
    }

    override fun remove(key: String) = defaults.removeObjectForKey(key)

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null
}

actual fun createPrefsStore(): PrefsStore = UserDefaultsPrefsStore()
