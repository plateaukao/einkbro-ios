import androidx.compose.ui.window.ComposeUIViewController
import info.plateaukao.einkbro.App
import info.plateaukao.einkbro.browser.ClearDataService
import info.plateaukao.einkbro.util.ExternalUrlBridge
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * Called from Swift (`onOpenURL`) when the app is opened via the `einkbro://`
 * scheme, an http(s) hand-off, or a `.webarchive` file. Hands the URL to the
 * running Compose UI, which opens it in a tab.
 */
fun handleExternalUrl(url: String) {
    ExternalUrlBridge.submit(url)
}

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
