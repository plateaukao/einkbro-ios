package info.plateaukao.einkbro

// internal: keeps DEBUG and friends out of the exported ObjC framework header,
// where the DEBUG name collides with Xcode's `#define DEBUG 1`.
internal object BuildConfig {
    const val DEBUG: Boolean = true
    const val VERSION_NAME: String = "0.1.0"
    const val APPLICATION_ID: String = "info.plateaukao.einkbro.ios"

    /**
     * Gates the "Data" (backup / restore) settings screen — the whole
     * export/import/LAN-share/Google-Drive group.
     *
     * Off for the first App Store submission: the flow is awkward to demo for
     * App Review (it needs a second device for LAN share, and Drive sync sends
     * the reviewer through a Google account sign-in). Flip to `true` to bring
     * the screen back; nothing else needs to change.
     */
    const val BACKUP_RESTORE_ENABLED: Boolean = false
}
