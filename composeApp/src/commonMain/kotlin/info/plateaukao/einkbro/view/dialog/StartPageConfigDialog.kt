package info.plateaukao.einkbro.view.dialog

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.StartPageRenderer
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.app_name
import info.plateaukao.einkbro.resources.start_page_clear_background
import info.plateaukao.einkbro.resources.start_page_edit_title
import info.plateaukao.einkbro.resources.start_page_set_background
import info.plateaukao.einkbro.util.FilePicker
import info.plateaukao.einkbro.util.blockingString

/**
 * Flow behind tapping the wordmark on the built-in start page (Android
 * StartPageConfigDialog): rename the heading or set/remove a background image
 * picked from the device.
 */
class StartPageConfigDialog(private val engine: WebViewEngine) {
    private val config get() = AppServices.config
    private val dialogManager get() = AppServices.dialogManager

    suspend fun show() {
        val options = mutableListOf(
            blockingString(Res.string.start_page_edit_title),
            blockingString(Res.string.start_page_set_background),
        )
        if (StartPageRenderer.hasBackground()) {
            options.add(blockingString(Res.string.start_page_clear_background))
        }

        when (dialogManager.getPlainListSelection(null, options)) {
            0 -> editTitle()
            // picker result lands asynchronously; the renderer saves + reloads
            1 -> FilePicker.pick { _, bytes ->
                StartPageRenderer.applyPickedBackground(engine, bytes)
            }
            2 -> {
                StartPageRenderer.deleteBackground()
                StartPageRenderer.loadStartPage(engine)
            }
        }
    }

    private suspend fun editTitle() {
        val title = dialogManager.getTextInput(
            Res.string.start_page_edit_title,
            defaultValue = StartPageRenderer.startPageTitle(),
        )?.trim() ?: return
        // typing the default back means "no customization"
        config.startPageTitle =
            if (title == blockingString(Res.string.app_name)) "" else title
        StartPageRenderer.loadStartPage(engine)
    }
}
