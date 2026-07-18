import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
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

// DoNothing: the default FocusableAboveKeyboard pans the whole scene against
// the Compose focus rect — which CMP 1.8 keeps stale forever (never cleared
// when a native view becomes first responder, fixed upstream only in 1.10).
// Focusing a text field inside the WKWebView then makes CMP setFrame the
// webview every frame of the keyboard animation while WKWebView runs its own
// caret-reveal scroll — the two fight and the page shakes (CMP-9238).
// Compose's own bottom-anchored inputs handle the keyboard via imePadding.
fun MainViewController() = ComposeUIViewController(
    configure = { onFocusBehavior = OnFocusBehavior.DoNothing }
) {
    // Clear-on-exit: iOS backgrounds rather than quits, so entering the
    // background is the practical "leaving the app" hook (opt-in via settings).
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = null,
    ) { ClearDataService.clearOnExitIfConfigured() }
    App()
}
