import androidx.compose.ui.window.ComposeUIViewController
import info.plateaukao.einkbro.App
import info.plateaukao.einkbro.browser.ClearDataService
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

fun MainViewController() = ComposeUIViewController {
    // Clear-on-exit: iOS backgrounds rather than quits, so entering the
    // background is the practical "leaving the app" hook (opt-in via settings).
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = null,
    ) { ClearDataService.clearOnExitIfConfigured() }
    App()
}
