package info.plateaukao.einkbro

// internal: keeps DEBUG and friends out of the exported ObjC framework header,
// where the DEBUG name collides with Xcode's `#define DEBUG 1`.
internal object BuildConfig {
    const val DEBUG: Boolean = true
    const val VERSION_NAME: String = "0.1.0"
    const val APPLICATION_ID: String = "info.plateaukao.einkbro.ios"

    /**
     * Gates the "Data" (backup / restore) settings screen — the whole
     * export/import/LAN-share/Google-Drive group — plus the two LAN menu
     * items (Send Link / Receive Data), which need the same multicast
     * entitlement.
     *
     * Off for the first App Store submission: the flow is awkward to demo for
     * App Review (it needs a second device for LAN share, and Drive sync sends
     * the reviewer through a Google account sign-in). Flip to `true` to bring
     * the screen back; nothing else needs to change.
     */
    const val BACKUP_RESTORE_ENABLED: Boolean = false

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
