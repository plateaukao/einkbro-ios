package info.plateaukao.einkbro.util

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual object PlatformActions {
    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    actual fun share(text: String) {
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    actual fun setAppLocale(languageTag: String) {
        val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults
        if (languageTag.isEmpty()) {
            defaults.removeObjectForKey("AppleLanguages")
        } else {
            defaults.setObject(listOf(languageTag), forKey = "AppleLanguages")
        }
    }

    private fun topViewController() = UIApplication.sharedApplication.keyWindow?.rootViewController
}
