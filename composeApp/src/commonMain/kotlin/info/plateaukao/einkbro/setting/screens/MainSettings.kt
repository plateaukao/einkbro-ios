package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.BuildConfig
import info.plateaukao.einkbro.activity.SettingRoute.Backup
import info.plateaukao.einkbro.activity.SettingRoute.Behavior
import info.plateaukao.einkbro.activity.SettingRoute.ChatGPT
import info.plateaukao.einkbro.activity.SettingRoute.DataControl
import info.plateaukao.einkbro.activity.SettingRoute.Gesture
import info.plateaukao.einkbro.activity.SettingRoute.Misc
import info.plateaukao.einkbro.activity.SettingRoute.Search
import info.plateaukao.einkbro.activity.SettingRoute.StartControl
import info.plateaukao.einkbro.activity.SettingRoute.Toolbar
import info.plateaukao.einkbro.activity.SettingRoute.Ui
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.LinkSettingItem
import info.plateaukao.einkbro.setting.NavigateSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.VersionSettingItem

fun buildMainSettingItems(): List<SettingItemInterface> = listOfNotNull(
    NavigateSettingItem(Res.string.setting_title_ui, Res.drawable.ic_phone, destination = Ui),
    NavigateSettingItem(
        Res.string.setting_title_toolbar,
        Res.drawable.ic_toolbar,
        destination = Toolbar
    ),
    NavigateSettingItem(
        Res.string.setting_title_behavior,
        Res.drawable.icon_ui,
        destination = Behavior
    ),
    NavigateSettingItem(
        Res.string.setting_gestures,
        Res.drawable.gesture_tap,
        destination = Gesture
    ),
    DividerSettingItem(),
    // Backup/restore is behind a build flag — see BuildConfig.BACKUP_RESTORE_ENABLED.
    if (BuildConfig.BACKUP_RESTORE_ENABLED) NavigateSettingItem(
        Res.string.setting_title_data,
        Res.drawable.icon_backup,
        destination = Backup
    ) else null,
    NavigateSettingItem(
        Res.string.setting_title_start_control,
        Res.drawable.icon_earth,
        destination = StartControl
    ),
    NavigateSettingItem(
        Res.string.setting_title_clear_control,
        Res.drawable.ic_data,
        destination = DataControl
    ),
    NavigateSettingItem(
        Res.string.setting_title_search,
        Res.drawable.icon_search,
        destination = Search
    ),
    DividerSettingItem(),
    NavigateSettingItem(
        Res.string.misc,
        Res.drawable.icon_dots,
        destination = Misc
    ),
    NavigateSettingItem(
        Res.string.setting_title_chat_gpt,
        Res.drawable.ic_chat_gpt,
        destination = ChatGPT
    ),
    LinkSettingItem.Manual,
    // No destination: the Android About screen only holds links to the
    // open-source project (site, releases, contributors, ...) and APK update
    // actions, none of which apply to the iOS build.
    VersionSettingItem(
        Res.string.menu_other_info,
        Res.drawable.icon_info,
    ),
)
