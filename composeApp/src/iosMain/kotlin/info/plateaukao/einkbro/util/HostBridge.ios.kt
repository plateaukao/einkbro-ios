package info.plateaukao.einkbro.util

import platform.UIKit.UIApplication

actual object HostBridge {

    actual fun setKeepAwake(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}
