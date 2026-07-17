package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.preference.FabPosition
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.EinkImageSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ValueSettingItem
import info.plateaukao.einkbro.view.EBToast

fun buildUiSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        // On Android this opens TranslationLanguageDialog.showAppLocale() and
        // recreates the activity when the locale changed.
        ActionSettingItem(
            Res.string.setting_app_locale,
            null,
            Res.string.setting_summary_app_locale,
        ) {
            EBToast.show(deps.context, "would open the app locale picker")
        },
        BooleanSettingItem(
            Res.string.hide_statusbar,
            null,
            Res.string.setting_summary_hide_statusbar,
            config.ui::hideStatusbar,
        ),
        BooleanSettingItem(
            Res.string.desktop_mode,
            null,
            Res.string.setting_summary_desktop,
            config.browser::desktop,
        ),
        BooleanSettingItem(
            Res.string.always_enable_zoom,
            null,
            Res.string.setting_summary_enable_zoom,
            config.display::enableZoom,
        ),
        BooleanSettingItem(
            Res.string.show_default_text_menu,
            null,
            Res.string.setting_summary_show_default_text_menu,
            config.ui::showDefaultActionMenu,
        ),
        BooleanSettingItem(
            Res.string.show_context_menu_icons,
            null,
            Res.string.setting_summary_show_context_menu_icons,
            config.ui::showActionMenuIcons,
        ),
        BooleanSettingItem(
            Res.string.show_history_thumbnail_grid,
            null,
            Res.string.setting_summary_show_history_thumbnail_grid,
            config.ui::showHistoryThumbnailGrid,
        ),
        DividerSettingItem(),
        ValueSettingItem(
            Res.string.setting_title_page_left_value,
            null,
            Res.string.setting_summary_page_left_value,
            config.touch::pageReservedOffsetInString
        ),
        // On Android this opens the ReaderSettingsDialogFragment chain
        // (reader settings -> font dialog -> font browser).
        ActionSettingItem(
            Res.string.reader_settings,
            null,
        ) {
            EBToast.show(deps.context, "would open the reader settings dialog")
        },
        ListSettingWithEnumItem(
            Res.string.dark_mode,
            null,
            Res.string.setting_summary_dark_mode,
            config.display::darkMode,
            listOf(
                Res.string.dark_mode_follow_system,
                Res.string.dark_mode_force_on,
                Res.string.dark_mode_disabled,
            )
        ),
        EinkImageSettingItem(
            Res.string.eink_image_adjustment,
            null,
            Res.string.eink_image_adjustment_summary,
            config.display::einkImageAdjustment,
            config.display::einkImageMode,
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_nav_pos,
            null,
            Res.string.setting_summary_nav_pos,
            config.ui::fabPosition,
            listOf(
                Res.string.setting_summary_nav_pos_right,
                Res.string.setting_summary_nav_pos_left,
                Res.string.setting_summary_nav_pos_center,
                Res.string.setting_summary_nav_pos_not_show,
                Res.string.setting_summary_nav_pos_custom,
            )
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_plus_behavior,
            null,
            Res.string.setting_summary_plus_behavior,
            config.tab::newTabBehavior,
            listOf(
                Res.string.plus_start_input_url,
                Res.string.plus_show_homepage,
                Res.string.plus_show_bookmarks,
            )
        ),
        ActionSettingItem(
            Res.string.setting_clear_recent_bookmarks,
            null,
            Res.string.setting_summary_clear_recent_bookmarks,
        ) {
            config.clearRecentBookmarks()
        },
        // On Android this opens MenuItemHideActivity.
        ActionSettingItem(
            Res.string.setting_title_hide_menu_items,
            null,
            Res.string.setting_summary_hide_menu_items,
        ) {
            deps.onOpenMenuItemHide()
        },
    )
}
