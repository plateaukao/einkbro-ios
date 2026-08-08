package info.plateaukao.einkbro.util

/**
 * Platform host behaviors the Compose UI can't do itself (parity Phase D).
 * Keep-awake and status-bar hiding are programmatic levers on iOS; forced
 * rotation is handled by the device — see the actual and PARITY_PLAN §7.
 */
expect object HostBridge {
    /** Prevents the screen from auto-locking while true (idle timer). */
    fun setKeepAwake(enabled: Boolean)

    /** Whether the OS is currently in dark appearance (DarkMode.SYSTEM). */
    fun isSystemDarkMode(): Boolean

    /** Hides the system status bar (clock/signal/battery overlay). */
    fun setStatusBarHidden(hidden: Boolean)

    /**
     * Whether the host can defer the bottom-edge system gesture (SwiftUI's
     * defersSystemGestures, iOS 16+). When false, bottom chrome must stay out
     * of the home-indicator band or its taps get eaten by the system.
     */
    val supportsBottomGestureDeferral: Boolean

    /**
     * Defers the home-indicator system gesture so touches in the bottom band
     * reach the app first; going Home then takes two consecutive swipes. Keep
     * it on only while interactive chrome actually occupies the band.
     */
    fun setDefersBottomSystemGesture(enabled: Boolean)
}
