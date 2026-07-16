package info.plateaukao.einkbro.setting.screens

import android.content.Context
import info.plateaukao.einkbro.preference.ConfigManager
import kotlinx.coroutines.CoroutineScope

/**
 * Minimal set of dependencies needed by the setting screen item builders.
 * The Android version carried a FragmentActivity and LifecycleCoroutineScope;
 * here a shim Context and a plain CoroutineScope stand in. Backup-related
 * actions are routed through [BackupOps] so the builders don't need direct
 * access to activity-result launchers.
 */
class SettingScreenDeps(
    val context: Context,
    val config: ConfigManager,
    val scope: CoroutineScope,
    val backupOps: BackupOps,
)

interface BackupOps {
    fun exportAppData()
    fun importAppData()
    fun shareAppData()
    fun receiveAppData()
    fun exportBookmarks()
    fun importBookmarks()
}
