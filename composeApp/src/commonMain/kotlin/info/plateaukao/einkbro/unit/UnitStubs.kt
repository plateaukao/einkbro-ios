package info.plateaukao.einkbro.unit

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.toast_copy_successful
import info.plateaukao.einkbro.util.PlatformActions
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.view.EBToast

/**
 * Stubs of the Android "unit" helpers used by the ported UI. Interactions
 * that can't happen in the catalog surface a toast so taps stay visible.
 */

object IntentUnit {
    // Android launches BrowserActivity with the URL; here the running
    // BrowserScreen collects the bridge and opens it in a new tab (file://
    // URLs route to the saved-page loader).
    fun launchUrl(context: Context, url: String) {
        info.plateaukao.einkbro.util.ExternalUrlBridge.submit(url)
    }

    fun showFile(context: Context, uri: Uri) {
        EBToast.show(context, "show file: $uri")
    }

    fun createResultLauncher(host: Any?, action: (Any?) -> Unit = {}): Any? = null

    fun gotoSettings(context: Context) {}

    fun gotoSystemTtsSettings(context: Context) {
        // iOS has no public deep link to the system voice list
        // (Settings > Accessibility > Spoken Content); app-settings: is the
        // closest public destination. Private App-Prefs: paths are rejected
        // by App Store review.
        PlatformActions.openUrl("app-settings:")
    }
    fun readCurrentArticle(context: Context) {}
}

object ShareUtil {
    fun copyToClipboard(context: Context, text: String) {
        PlatformActions.copyToClipboard(text)
        EBToast.show(context, Res.string.toast_copy_successful)
    }

    fun startServingFile(context: Context, port: Int = 8080) {}
    fun startReceivingFile(context: Context, port: Int = 8080) {}
    fun stopBroadcast() {}
}

object HelperUnit {
    fun parseMarkdown(markdownText: String): AnnotatedString =
        MarkdownParser.parseMarkdown(markdownText)

    fun getStringFromAsset(fileName: String): String = ""

    fun fileName(url: String?): String {
        val raw = url?.substringAfterLast('/')?.substringBefore('?').orEmpty()
        return raw.ifEmpty { "download" }
    }
}

object BrowserUnit {
    fun createFilePicker(launcher: Any?, name: String = ""): Any? = null

    fun restartApp(context: Context) {
        EBToast.show(context, "restart is not applicable in the iOS catalog")
    }
}

class BackupUnit(private val context: Context = Context()) {
    fun exportBookmarks(launcher: Any? = null) {}
    fun importBookmarks(launcher: Any? = null) {}
}

object LocaleManager {
    fun setLocale(context: Context, language: String = "") {}
    fun getLocaleString(): String = "en"
}

object EinkImageProcessor
