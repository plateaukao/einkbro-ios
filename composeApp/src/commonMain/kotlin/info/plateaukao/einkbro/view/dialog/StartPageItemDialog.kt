package info.plateaukao.einkbro.view.dialog

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.StartPageRenderer
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.preference.StartPageItem
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.dialog_title_hint
import info.plateaukao.einkbro.resources.dialog_url_hint
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.start_page_enter_manually
import info.plateaukao.einkbro.resources.start_page_pick_bookmark
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.blockingString

/**
 * Flow behind the "+" tile on the built-in start page (Android
 * StartPageItemDialog): add an item picked from the existing bookmarks or
 * entered manually, or delete a current item. Android's manual entry is one
 * dialog with two fields; DialogManager's text input takes one field, so
 * title and url are asked in sequence instead.
 */
class StartPageItemDialog(private val engine: WebViewEngine) {
    private val config get() = AppServices.config
    private val dialogManager get() = AppServices.dialogManager
    private val bookmarkManager get() = AppServices.bookmarkManager

    suspend fun show() {
        val options = mutableListOf(
            blockingString(Res.string.start_page_pick_bookmark),
            blockingString(Res.string.start_page_enter_manually),
        )
        if (config.startPageItems.isNotEmpty()) {
            options.add(blockingString(Res.string.menu_delete))
        }

        when (dialogManager.getPlainListSelection(null, options)) {
            0 -> pickFromBookmarks()
            1 -> enterManually()
            2 -> removeItem()
        }
    }

    private suspend fun pickFromBookmarks() {
        val bookmarks = bookmarkManager.getAllBookmarks().filterNot { it.isDirectory }
        if (bookmarks.isEmpty()) return
        val index = dialogManager.getPlainListSelection(
            blockingString(Res.string.start_page_pick_bookmark),
            bookmarks.map { it.title },
        ) ?: return
        val bookmark = bookmarks.getOrNull(index) ?: return
        config.addStartPageItem(StartPageItem(bookmark.title, bookmark.url))
        StartPageRenderer.loadStartPage(engine)
    }

    private suspend fun enterManually() {
        val title = dialogManager.getTextInput(
            Res.string.dialog_title_hint, defaultValue = ""
        ) ?: return
        val rawUrl = dialogManager.getTextInput(
            Res.string.dialog_url_hint, defaultValue = ""
        )?.trim() ?: return
        if (rawUrl.isEmpty()) return
        val url = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
        val finalTitle = title.trim().ifEmpty { Uri.parse(url).host ?: url }
        config.addStartPageItem(StartPageItem(finalTitle, url))
        StartPageRenderer.loadStartPage(engine)
    }

    private suspend fun removeItem() {
        val items = config.startPageItems
        val index = dialogManager.getPlainListSelection(
            blockingString(Res.string.menu_delete),
            items.map { it.title },
        ) ?: return
        val item = items.getOrNull(index) ?: return
        config.removeStartPageItem(item.url)
        StartPageRenderer.loadStartPage(engine)
    }
}
