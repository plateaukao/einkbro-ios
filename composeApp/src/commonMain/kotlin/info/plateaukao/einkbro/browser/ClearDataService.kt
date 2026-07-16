package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Clears browsing data, the iOS counterpart of Android's ClearService.
 * Web-managed data (cache/cookies/local storage) goes through [WebDataCleaner];
 * history lives in Room and is cleared directly.
 */
object ClearDataService {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun clear(
        cache: Boolean,
        cookies: Boolean,
        history: Boolean,
        localStorage: Boolean,
        onDone: () -> Unit = {},
    ) {
        if (history) {
            scope.launch { AppServices.database.historyDao().deleteAll() }
        }
        WebDataCleaner.clear(cache = cache, cookies = cookies, localStorage = localStorage, onDone = onDone)
    }

    /** Runs the clear-on-exit flags (no-op unless the user enabled clear-on-quit). */
    fun clearOnExitIfConfigured() {
        val config = AppServices.config
        if (!config.clearWhenQuit) return
        clear(
            cache = config.clearCache,
            cookies = config.clearCookies,
            history = config.clearHistory,
            localStorage = config.clearIndexedDB,
        )
    }
}
