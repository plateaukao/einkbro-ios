package info.plateaukao.einkbro.util

/**
 * Platform host behaviors the Compose UI can't do itself (parity Phase D).
 * Keep-awake and status-bar hiding are programmatic levers on iOS; forced
 * rotation is handled by the device — see the actual and PARITY_PLAN §7.
 */
expect object HostBridge {
    /** Prevents the screen from auto-locking while true (idle timer). */
    fun setKeepAwake(enabled: Boolean)

    /** Hides the system status bar (clock/signal/battery overlay). */
    fun setStatusBarHidden(hidden: Boolean)
}
