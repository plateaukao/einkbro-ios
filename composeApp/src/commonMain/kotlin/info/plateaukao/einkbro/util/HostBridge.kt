package info.plateaukao.einkbro.util

/**
 * Platform host behaviors the Compose UI can't do itself (parity Phase D).
 * Only keep-awake is a clean programmatic lever on iOS; status-bar hiding and
 * forced rotation are handled visually (Compose insets) / by the device
 * respectively — see the actual and PARITY_PLAN §7 for why.
 */
expect object HostBridge {
    /** Prevents the screen from auto-locking while true (idle timer). */
    fun setKeepAwake(enabled: Boolean)
}
