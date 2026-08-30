package info.plateaukao.einkbro

// internal: keeps DEBUG and friends out of the exported ObjC framework header,
// where the DEBUG name collides with Xcode's `#define DEBUG 1`.
internal object BuildConfig {
    const val DEBUG: Boolean = true
    // Read from the app bundle so the About row always matches the shipped
    // CFBundleShortVersionString — a hardcoded copy here rotted at "0.1.0"
    // while the real version moved on.
    val VERSION_NAME: String get() = appVersionName()
    const val APPLICATION_ID: String = "info.plateaukao.einkbro.ios"

    // Backup/restore is a runtime unlock, not a build flag: see
    // ConfigManager.isBackupRestoreUnlocked (einkbro://googlesync in the URL bar).

    /**
     * Gates the Instapaper menu item and its gesture action.
     *
     * Off for the first App Store submission: "save to Instapaper" only does
     * anything once the user hands over an Instapaper username and password,
     * so App Review would find a dead item behind a credentials prompt.
     * Flip to `true` to bring it back; the dialog and repository stay wired.
     */
    const val INSTAPAPER_ENABLED: Boolean = false
}

/** The marketing version of the running app (CFBundleShortVersionString). */
internal expect fun appVersionName(): String
