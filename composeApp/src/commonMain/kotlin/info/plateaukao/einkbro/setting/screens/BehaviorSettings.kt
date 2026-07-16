package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.preference.ShareLongPressAction
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.SettingItemInterface

fun buildBehaviorSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        // Tab behavior
        BooleanSettingItem(
            Res.string.setting_title_saveTabs,
            null,
            Res.string.setting_summary_saveTabs,
            config.tab::shouldSaveTabs,
        ),
        BooleanSettingItem(
            Res.string.setting_title_background_loading,
            null,
            Res.string.setting_summary_background_loading,
            config.tab::enableWebBkgndLoad,
        ),
        BooleanSettingItem(
            Res.string.setting_title_next_tab,
            null,
            Res.string.setting_summary_next_tab,
            config.tab::shouldShowNextAfterRemoveTab,
        ),
        BooleanSettingItem(
            Res.string.settings_title_back_key_behavior,
            null,
            Res.string.settings_summary_back_key_behavior,
            config.tab::closeTabWhenNoMoreBackHistory,
        ),
        BooleanSettingItem(
            Res.string.setting_title_confirm_tab_close,
            null,
            Res.string.setting_summary_confirm_tab_close,
            config.tab::confirmTabClose,
        ),
        DividerSettingItem(),
        // URL & navigation
        BooleanSettingItem(
            Res.string.setting_title_trim_input_url,
            null,
            Res.string.setting_summary_trim_input_url,
            config.browser::shouldTrimInputUrl,
        ),
        BooleanSettingItem(
            Res.string.setting_title_prune_query_parameter,
            null,
            Res.string.setting_summary_prune_query_parameter,
            config.browser::shouldPruneQueryParameters,
        ),
        BooleanSettingItem(
            Res.string.setting_title_enable_url_drag_to_action,
            null,
            Res.string.setting_summary_enable_url_drag_to_action,
            config.touch::enableDragUrlToAction,
        ),
        BooleanSettingItem(
            Res.string.setting_title_show_bookmarks_input_bar,
            null,
            Res.string.setting_summary_show_bookmarks_input_bar,
            config.browser::showBookmarksInInputBar,
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_share_long_press,
            null,
            Res.string.setting_summary_share_long_press,
            config.browser::shareLongPressAction,
            ShareLongPressAction.entries.map { it.labelResId },
        ),
        DividerSettingItem(),
        // Video
        BooleanSettingItem(
            Res.string.setting_title_video_autoplay,
            null,
            Res.string.setting_summary_video_autoplay,
            config.browser::enableVideoAutoplay,
        ),
        BooleanSettingItem(
            Res.string.setting_title_video_auto_fullscreen,
            null,
            Res.string.setting_summary_video_auto_fullscreen,
            config.browser::enableVideoAutoFullscreen,
        ),
        BooleanSettingItem(
            Res.string.setting_title_video_pip,
            null,
            Res.string.setting_summary_video_pip,
            config.browser::enableVideoPip,
        ),
        DividerSettingItem(),
        // Input & controls
        BooleanSettingItem(
            Res.string.setting_title_vi_binding,
            null,
            Res.string.setting_summary_vi_binding,
            config.browser::enableViBinding,
        ),
        BooleanSettingItem(
            Res.string.setting_title_disable_long_press_toucharea,
            null,
            Res.string.setting_summary_disable_long_press_toucharea,
            config.touch::disableLongPressTouchArea,
        ),
        BooleanSettingItem(
            Res.string.setting_title_useUpDown,
            null,
            Res.string.setting_summary_useUpDownKey,
            config.touch::useUpDownPageTurn,
        ),
        BooleanSettingItem(
            Res.string.setting_title_enable_pull_to_refresh,
            null,
            Res.string.setting_summary_enable_pull_to_refresh,
            config.browser::enablePullToRefresh,
        ),
        DividerSettingItem(),
        // Display & rendering
        BooleanSettingItem(
            Res.string.setting_title_screen_awake,
            null,
            Res.string.setting_summary_screen_awake,
            config.ui::keepAwake,
        ),
        BooleanSettingItem(
            Res.string.setting_title_text_wrap_reflow,
            null,
            Res.string.setting_summary_text_wrap_reflow,
            config.display::enableZoomTextWrapReflow,
        ),
        BooleanSettingItem(
            Res.string.setting_title_zoom_in_custom_view,
            null,
            Res.string.setting_summary_zoom_in_custom_view,
            config.display::zoomInCustomView,
        ),
        DividerSettingItem(),
        // Security & network
        BooleanSettingItem(
            Res.string.setting_title_enable_ssl_error_dialog,
            null,
            Res.string.setting_summary_enable_ssl_error_dialog,
            config.browser::enableCertificateErrorDialog,
        ),
        BooleanSettingItem(
            Res.string.setting_title_enable_web_cache,
            null,
            Res.string.setting_summary_enabling_web_cache,
            config.browser::webLoadCacheFirst,
        ),
    )
}
