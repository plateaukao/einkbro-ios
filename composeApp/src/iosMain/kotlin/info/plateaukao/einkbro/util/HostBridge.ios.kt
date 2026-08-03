package info.plateaukao.einkbro.util

import platform.UIKit.UIApplication
import platform.UIKit.UIDevice

actual object HostBridge {

    actual fun setKeepAwake(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }

    /**
     * Swift owns the real status-bar lever: scene-based apps ignore the legacy
     * UIApplication setter (not even exported to Kotlin), so iOSApp.swift
     * registers here and applies the value via SwiftUI's statusBar(hidden:).
     * Registration replays the latest value so startup order doesn't matter.
     */
    var statusBarHiddenListener: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(statusBarHidden)
        }
    private var statusBarHidden = false

    actual fun setStatusBarHidden(hidden: Boolean) {
        statusBarHidden = hidden
        statusBarHiddenListener?.invoke(hidden)
    }

    // SwiftUI's defersSystemGestures(on:) only exists on iOS 16+; on 15 the
    // Compose side keeps the Safari-style padding instead.
    actual val supportsBottomGestureDeferral: Boolean =
        (UIDevice.currentDevice.systemVersion
            .split(".").firstOrNull()?.toIntOrNull() ?: 0) >= 16

    /**
     * Same Swift-owned-lever pattern as the status bar: UIKit resolves gesture
     * deferral through the window's root view controller (SwiftUI's hosting
     * controller), so a preference set on the wrapped Compose VC is ignored —
     * iOSApp.swift registers here and applies defersSystemGestures(on:).
     */
    var defersBottomSystemGestureListener: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            value?.invoke(defersBottomSystemGesture)
        }
    private var defersBottomSystemGesture = false

    actual fun setDefersBottomSystemGesture(enabled: Boolean) {
        defersBottomSystemGesture = enabled
        defersBottomSystemGestureListener?.invoke(enabled)
    }
}
