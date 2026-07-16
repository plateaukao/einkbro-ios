package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.browser.ClearDataService
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.view.EBToast

fun buildClearDataSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        BooleanSettingItem(
            Res.string.clear_title_cache,
            null,
            config = config::clearCache,
        ),
        BooleanSettingItem(
            Res.string.clear_title_history,
            null,
            config = config::clearHistory,
        ),
        BooleanSettingItem(
            Res.string.clear_title_indexedDB,
            null,
            config = config::clearIndexedDB,
        ),
        BooleanSettingItem(
            Res.string.clear_title_cookie,
            null,
            Res.string.setting_summary_cookie_delete,
            config::clearCookies
        ),
        BooleanSettingItem(
            Res.string.clear_title_quit,
            null,
            Res.string.clear_summary_quit,
            config::clearWhenQuit
        ),
        ActionSettingItem(
            Res.string.clear_title_deleteDatabase,
            null,
            Res.string.clear_summary_deleteDatabase,
        ) {
            // Clears web data (cache/cookies/local storage) and Room history now.
            ClearDataService.clear(cache = true, cookies = true, history = true, localStorage = true) {
                EBToast.show(deps.context, "Browsing data cleared")
            }
        }
    )
}
