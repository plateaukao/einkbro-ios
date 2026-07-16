package info.plateaukao.einkbro.util

/**
 * Small platform seam for the OS-level actions the selection / context menus
 * need (clipboard, share sheet, open-in-default-app). Android's ShareUtil /
 * IntentUnit equivalents; the iosMain actual talks to UIKit.
 */
expect object PlatformActions {
    fun copyToClipboard(text: String)
    fun share(text: String)
    fun openUrl(url: String)
}
