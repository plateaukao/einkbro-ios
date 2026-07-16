package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface

fun buildBackupSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val backupOps = deps.backupOps
    return listOf(
        ActionSettingItem(
            Res.string.setting_title_export_appData,
            null,
            Res.string.setting_summary_export_appData
        ) { backupOps.exportAppData() },
        ActionSettingItem(
            Res.string.setting_title_import_appData,
            null,
            Res.string.setting_summary_import_appData
        ) { backupOps.importAppData() },
        ActionSettingItem(
            Res.string.setting_title_share_appData,
            null,
            Res.string.setting_summary_share_appData
        ) { backupOps.shareAppData() },
        ActionSettingItem(
            Res.string.setting_title_receive_appData,
            null,
            Res.string.setting_summary_receive_appData
        ) { backupOps.receiveAppData() },
        DividerSettingItem(),
        ActionSettingItem(
            Res.string.setting_title_export_bookmarks,
            null,
        ) { backupOps.exportBookmarks() },
        ActionSettingItem(
            Res.string.setting_title_import_bookmarks,
            null,
        ) { backupOps.importBookmarks() },
    )
}
