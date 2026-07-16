package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ValueSettingItem

fun buildUserAgentSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        BooleanSettingItem(
            Res.string.setting_title_userAgent_toggle,
            null,
            Res.string.setting_summary_userAgent_toggle,
            config.browser::enableCustomUserAgent
        ),
        ValueSettingItem(
            Res.string.setting_title_userAgent,
            null,
            Res.string.setting_summary_userAgent,
            config.browser::customUserAgent
        ),
    )
}
