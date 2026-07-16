package info.plateaukao.einkbro.setting.screens

import android.content.Context
import info.plateaukao.einkbro.activity.WhiteListType
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
    /** Opens the userscript manager (parity Phase H); wired by the host screen. */
    val onOpenUserScripts: () -> Unit = {},
    /** Opens the GPT action editor (parity Phase K); wired by the host screen. */
    val onOpenGptActions: () -> Unit = {},
    /** Opens the persisted GPT query history (parity Phase K); wired by the host screen. */
    val onOpenGptQueries: () -> Unit = {},
    /** Opens the toolbar-icon editor (parity Phase N). */
    val onOpenToolbarConfig: () -> Unit = {},
    /** Opens the custom-statusbar item editor (parity Phase N). */
    val onOpenStatusbarConfig: () -> Unit = {},
    /** Opens the ad-block update/settings screen (parity Phase N). */
    val onOpenAdBlockSettings: () -> Unit = {},
    /** Opens a per-type whitelist editor (adblock/JS/cookie/split-search, Phase N). */
    val onOpenWhitelist: (WhiteListType) -> Unit = {},
)

interface BackupOps {
    fun exportAppData()
    fun importAppData()
    fun shareAppData()
    fun receiveAppData()
    fun exportBookmarks()
    fun importBookmarks()
}
