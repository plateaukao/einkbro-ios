package info.plateaukao.einkbro.util

import platform.UIKit.UIApplication

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
}
