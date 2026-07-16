package info.plateaukao.einkbro.browser

import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWebsiteDataTypeCookies
import platform.WebKit.WKWebsiteDataTypeDiskCache
import platform.WebKit.WKWebsiteDataTypeIndexedDBDatabases
import platform.WebKit.WKWebsiteDataTypeLocalStorage
import platform.WebKit.WKWebsiteDataTypeMemoryCache
import platform.WebKit.WKWebsiteDataTypeSessionStorage
import platform.WebKit.WKWebsiteDataTypeWebSQLDatabases

actual object WebDataCleaner {
    actual fun clear(
        cache: Boolean,
        cookies: Boolean,
        localStorage: Boolean,
        onDone: () -> Unit,
    ) {
        val types = mutableSetOf<String>()
        if (cache) {
            types.add(WKWebsiteDataTypeDiskCache)
            types.add(WKWebsiteDataTypeMemoryCache)
        }
        if (cookies) types.add(WKWebsiteDataTypeCookies)
        if (localStorage) {
            types.add(WKWebsiteDataTypeLocalStorage)
            types.add(WKWebsiteDataTypeSessionStorage)
            types.add(WKWebsiteDataTypeIndexedDBDatabases)
            types.add(WKWebsiteDataTypeWebSQLDatabases)
        }
        if (types.isEmpty()) {
            onDone()
            return
        }
        val epoch = NSDate.dateWithTimeIntervalSince1970(0.0)
        WKWebsiteDataStore.defaultDataStore()
            .removeDataOfTypes(types, epoch) { onDone() }
    }
}
