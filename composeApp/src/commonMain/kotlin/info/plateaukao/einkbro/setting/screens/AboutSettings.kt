package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.LinkSettingItem
import info.plateaukao.einkbro.setting.ProgressActionSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.view.EBToast
import kotlinx.coroutines.delay

fun buildAboutSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> =
    mutableListOf<SettingItemInterface>().apply {
        addAll(LinkSettingItem.entries.filter { it != LinkSettingItem.Manual })
        add(DividerSettingItem())
        // On Android these download an APK from GitHub (shown only when
        // BuildConfig.showUpdateButton); here the progress flow is simulated
        // so the ProgressActionSettingItem UI can be exercised.
        add(
            ProgressActionSettingItem(
                Res.string.setting_title_github_update,
                null,
            ) { progressCallback ->
                for (step in 1..10) {
                    delay(150)
                    progressCallback.updateProgress(step / 10f)
                }
                EBToast.show(deps.context, "would download the latest release APK (Android-only)")
            })
        add(
            ProgressActionSettingItem(
                Res.string.setting_title_github_snapshot,
                null,
            ) { progressCallback ->
                for (step in 1..10) {
                    delay(150)
                    progressCallback.updateProgress(step / 10f)
                }
                EBToast.show(deps.context, "would download the snapshot APK (Android-only)")
            })
        add(DividerSettingItem())
    }
