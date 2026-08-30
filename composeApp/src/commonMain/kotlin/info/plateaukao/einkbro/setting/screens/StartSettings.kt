package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.activity.WhiteListType
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.view.EBToast

fun buildStartSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ActionSettingItem(
            Res.string.setting_title_site_rules,
            null,
            Res.string.setting_summary_site_rules,
        ) { deps.onOpenSiteRules() },
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.setting_title_images,
            null,
            Res.string.setting_summary_images,
            config.browser::enableImages
        ),
        BooleanSettingItem(
            Res.string.setting_title_auto_fill_form,
            null,
            Res.string.setting_summary_auto_fill_form,
            config.browser::autoFillForm
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_history,
            null,
            Res.string.setting_summary_history,
            config.tab::saveHistoryMode,
            listOf(
                Res.string.save_history_mode_save_when_open,
                Res.string.save_history_mode_save_when_close,
                Res.string.save_history_mode_disabled,
            )
        ),
        BooleanSettingItem(
            Res.string.setting_title_debug,
            null,
            Res.string.setting_summary_debug,
            config.browser::debugWebView
        ),
        BooleanSettingItem(
            Res.string.setting_title_remote,
            null,
            Res.string.setting_summary_remote,
            config.browser::enableRemoteAccess
        ),
        BooleanSettingItem(
            Res.string.setting_title_location,
            null,
            Res.string.setting_summary_location,
            config.browser::shareLocation
        ),
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.setting_title_adblock,
            null,
            Res.string.setting_summary_adblock,
            config.browser::adBlock
        ),
        // On Android this opens AdBlockSettingActivity.
        ActionSettingItem(
            Res.string.setting_title_update_adblock,
            null,
            Res.string.setting_summary_update_adblock,
        ) {
            deps.onOpenAdBlockSettings()
        },
        // On Android this opens DataListActivity(WhiteListType.Adblock).
        ActionSettingItem(
            Res.string.setting_title_whitelist,
            null,
            Res.string.setting_summary_whitelist,
        ) {
            deps.onOpenWhitelist(WhiteListType.Adblock)
        },
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.setting_title_javascript,
            null,
            Res.string.setting_summary_javascript,
            config.browser::enableJavascript
        ),
        // On Android this opens DataListActivity(WhiteListType.Javascript).
        ActionSettingItem(
            Res.string.setting_title_whitelistJS,
            null,
            Res.string.setting_summary_whitelistJS,
        ) {
            deps.onOpenWhitelist(WhiteListType.Javascript)
        },
        // Opens the userscript manager (parity Phase H).
        ActionSettingItem(
            Res.string.setting_title_userscripts,
            null,
            Res.string.setting_summary_userscripts,
        ) { deps.onOpenUserScripts() },
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.setting_title_cookie,
            null,
            Res.string.setting_summary_cookie,
            config.browser::cookies
        ),
        // On Android this opens DataListActivity(WhiteListType.Cookie).
        ActionSettingItem(
            Res.string.setting_title_whitelistCookie,
            null,
            Res.string.setting_summary_whitelistCookie,
        ) {
            deps.onOpenWhitelist(WhiteListType.Cookie)
        },
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.setting_title_save_data,
            null,
            Res.string.setting_summary_save_data,
            config.browser::enableSaveData
        ),
    )
}
