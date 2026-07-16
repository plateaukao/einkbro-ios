package info.plateaukao.einkbro.util

import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue

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

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    override fun exportPrefs(prefix: String): String {
        val all = defaults.dictionaryRepresentation()
        val out = NSMutableDictionary()
        all.keys.forEach { key ->
            val k = key as? String ?: return@forEach
            if (k.startsWith(prefix)) out.setValue(all[key], forKey = k)
        }
        val data = NSJSONSerialization.dataWithJSONObject(out, options = 0uL, error = null)
            ?: return "{}"
        return NSString.create(data, NSUTF8StringEncoding) as String? ?: "{}"
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    override fun importPrefs(json: String) {
        val data = (json as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        @Suppress("UNCHECKED_CAST")
        val obj = NSJSONSerialization.JSONObjectWithData(data, options = 0uL, error = null)
            as? Map<Any?, *> ?: return
        obj.forEach { (key, value) ->
            (key as? String)?.let { defaults.setObject(value, it) }
        }
    }
}

actual fun createPrefsStore(): PrefsStore = UserDefaultsPrefsStore()
