package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ToolbarPositionSettingItem
import info.plateaukao.einkbro.view.EBToast

fun buildToolbarSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        DividerSettingItem(Res.string.setting_section_toolbar),
        ToolbarPositionSettingItem(
            Res.string.setting_title_toolbar_position,
            null,
            null,
            config.ui::toolbarPosition,
        ),
        BooleanSettingItem(
            Res.string.setting_title_show_tab_bar,
            null,
            Res.string.setting_summary_show_tab_bar,
            config.tab::shouldShowTabBar,
        ),
        // On Android this opens ToolbarConfigActivity.
        ActionSettingItem(
            Res.string.toolbar_icons,
            null,
            Res.string.toolbar_icons_description,
        ) {
            EBToast.show(deps.context, "would open the toolbar icons editor")
        },
        BooleanSettingItem(
            Res.string.setting_title_hideToolbar,
            null,
            Res.string.setting_summary_hide,
            config.ui::shouldHideToolbar,
        ),
        BooleanSettingItem(
            Res.string.setting_title_toolbarShow,
            null,
            Res.string.setting_summary_toolbarShow,
            config.ui::showToolbarFirst,
        ),
        DividerSettingItem(Res.string.setting_section_statusbar),
        BooleanSettingItem(
            Res.string.setting_title_statusbar_enabled,
            null,
            Res.string.setting_summary_statusbar_enabled,
            config.ui::statusbarEnabled,
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_statusbar_position,
            null,
            null,
            config.ui::statusbarPosition,
            listOf(
                Res.string.statusbar_position_top,
                Res.string.statusbar_position_bottom,
            )
        ),
        // On Android this opens StatusbarConfigActivity.
        ActionSettingItem(
            Res.string.setting_title_statusbar_items,
            null,
            Res.string.setting_summary_statusbar_items,
        ) {
            EBToast.show(deps.context, "would open the statusbar items editor")
        },
    )
}
