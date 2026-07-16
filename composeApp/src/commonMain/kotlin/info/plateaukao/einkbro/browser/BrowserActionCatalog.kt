package info.plateaukao.einkbro.browser

import org.jetbrains.compose.resources.StringResource
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.view.GestureType

data class BrowserActionEntry(
    val action: BrowserAction,
    val labelResId: StringResource,
) {
    val id: String get() = action::class.simpleName.orEmpty()
}

data class BrowserActionCategory(
    val titleResId: StringResource,
    val entries: List<BrowserActionEntry>,
)

object BrowserActionCatalog {
    val categories: List<BrowserActionCategory> = listOf(
        BrowserActionCategory(
            Res.string.action_category_navigation,
            listOf(
                BrowserActionEntry(BrowserAction.GoForward, Res.string.forward_in_history),
                BrowserActionEntry(BrowserAction.HandleBackKey, Res.string.back),
                BrowserActionEntry(BrowserAction.RefreshAction, Res.string.refresh),
                BrowserActionEntry(BrowserAction.JumpToTop, Res.string.scroll_to_top),
                BrowserActionEntry(BrowserAction.JumpToBottom, Res.string.scroll_to_bottom),
                BrowserActionEntry(BrowserAction.PageUp, Res.string.page_up),
                BrowserActionEntry(BrowserAction.PageDown, Res.string.page_down),
                BrowserActionEntry(BrowserAction.SendPageUpKey, Res.string.key_page_up),
                BrowserActionEntry(BrowserAction.SendPageDownKey, Res.string.key_page_down),
                BrowserActionEntry(BrowserAction.SendLeftKey, Res.string.key_left),
                BrowserActionEntry(BrowserAction.SendRightKey, Res.string.key_right),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_tab,
            listOf(
                BrowserActionEntry(BrowserAction.NewATab, Res.string.open_new_tab),
                BrowserActionEntry(BrowserAction.DuplicateTab, Res.string.duplicate_tab),
                BrowserActionEntry(BrowserAction.RemoveAlbum, Res.string.close_tab),
                BrowserActionEntry(BrowserAction.GotoLeftTab, Res.string.switch_to_left_tab),
                BrowserActionEntry(BrowserAction.GotoRightTab, Res.string.switch_to_right_tab),
                BrowserActionEntry(BrowserAction.ShowOverview, Res.string.show_overview),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_content,
            listOf(
                BrowserActionEntry(BrowserAction.ToggleReaderMode, Res.string.reader_mode),
                BrowserActionEntry(BrowserAction.ToggleVerticalRead, Res.string.vertical_read),
                BrowserActionEntry(BrowserAction.IncreaseFontSize, Res.string.font_size_increase),
                BrowserActionEntry(BrowserAction.DecreaseFontSize, Res.string.font_size_decrease),
                BrowserActionEntry(BrowserAction.ShowFontSizeChangeDialog, Res.string.font_size),
                BrowserActionEntry(BrowserAction.ShowFontBoldnessDialog, Res.string.bold_font),
                BrowserActionEntry(BrowserAction.ShowReaderSettingsDialog, Res.string.reader_settings),
                BrowserActionEntry(BrowserAction.InvertColors, Res.string.menu_invert_color),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_view,
            listOf(
                BrowserActionEntry(BrowserAction.ToggleFullscreen, Res.string.fullscreen),
                BrowserActionEntry(BrowserAction.RotateScreen, Res.string.rotate),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_translation,
            listOf(
                BrowserActionEntry(BrowserAction.ShowTranslation, Res.string.translate),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_tts,
            listOf(
                BrowserActionEntry(BrowserAction.HandleTtsButton, Res.string.menu_tts),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_bookmarks,
            listOf(
                BrowserActionEntry(BrowserAction.OpenBookmarkPage, Res.string.bookmarks),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_search,
            listOf(
                BrowserActionEntry(BrowserAction.ShowSearchPanel, Res.string.setting_title_search),
                BrowserActionEntry(BrowserAction.ToggleTextSearch, Res.string.action_text_search),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_share,
            listOf(
                BrowserActionEntry(BrowserAction.ShareLink, Res.string.menu_share_link),
                BrowserActionEntry(BrowserAction.AddToInstapaper, Res.string.menu_instapaper),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_touch,
            listOf(
                BrowserActionEntry(BrowserAction.ToggleTouchTurnPage, Res.string.toggle_touch_turn_page),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_ai,
            listOf(
                BrowserActionEntry(BrowserAction.SummarizeContent, Res.string.action_summarize_content),
                BrowserActionEntry(BrowserAction.ChatWithWeb(), Res.string.chat_with_web),
                BrowserActionEntry(BrowserAction.ShowPageAiActionMenu, Res.string.page_ai),
                BrowserActionEntry(BrowserAction.ShowTaskMenu, Res.string.task_menu_title),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_file,
            listOf(
                BrowserActionEntry(BrowserAction.ShowEpubDialog, Res.string.menu_save_epub),
                BrowserActionEntry(BrowserAction.SavePageForLater, Res.string.menu_save_archive),
                BrowserActionEntry(BrowserAction.SaveWebArchive, Res.string.action_save_web_archive),
            ),
        ),
        BrowserActionCategory(
            Res.string.action_category_dialog,
            listOf(
                BrowserActionEntry(BrowserAction.FocusOnInput, Res.string.input_url),
                BrowserActionEntry(BrowserAction.ShowMenuDialog, Res.string.menu),
                BrowserActionEntry(BrowserAction.ShowFastToggleDialog, Res.string.action_fast_toggle),
                BrowserActionEntry(BrowserAction.ShowSiteSettingsDialog, Res.string.site_settings),
                BrowserActionEntry(BrowserAction.ShowUserScriptCommands, Res.string.setting_title_userscripts),
            ),
        ),
    )

    private val entriesById: Map<String, BrowserActionEntry> =
        categories.flatMap { it.entries }.associateBy { it.id }

    val nothingEntry: BrowserActionEntry =
        BrowserActionEntry(BrowserAction.Noop, Res.string.nothing)

    fun entryOf(id: String): BrowserActionEntry =
        if (id == nothingEntry.id) nothingEntry else (entriesById[id] ?: nothingEntry)

    fun entryOf(action: BrowserAction): BrowserActionEntry =
        entryOf(action::class.simpleName.orEmpty())

    fun idOf(action: BrowserAction): String = action::class.simpleName.orEmpty()

    fun migrateLegacyId(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        // New format: BrowserAction subclass simple name (non-numeric).
        if (stored.firstOrNull()?.isDigit() != true) return stored
        // Legacy format: 2-char GestureType code like "01".
        val legacy = GestureType.from(stored)
        return legacyGestureToActionId[legacy] ?: nothingEntry.id
    }

    private val legacyGestureToActionId: Map<GestureType, String> = mapOf(
        GestureType.NothingHappen to nothingEntry.id,
        GestureType.Forward to idOf(BrowserAction.GoForward),
        GestureType.Backward to idOf(BrowserAction.HandleBackKey),
        GestureType.ScrollToTop to idOf(BrowserAction.JumpToTop),
        GestureType.ScrollToBottom to idOf(BrowserAction.JumpToBottom),
        GestureType.ToLeftTab to idOf(BrowserAction.GotoLeftTab),
        GestureType.ToRightTab to idOf(BrowserAction.GotoRightTab),
        GestureType.Overview to idOf(BrowserAction.ShowOverview),
        GestureType.OpenNewTab to idOf(BrowserAction.NewATab),
        GestureType.CloseTab to idOf(BrowserAction.RemoveAlbum),
        GestureType.PageUp to idOf(BrowserAction.PageUp),
        GestureType.PageDown to idOf(BrowserAction.PageDown),
        GestureType.Bookmark to idOf(BrowserAction.OpenBookmarkPage),
        GestureType.Back to idOf(BrowserAction.HandleBackKey),
        GestureType.Fullscreen to idOf(BrowserAction.ToggleFullscreen),
        GestureType.Refresh to idOf(BrowserAction.RefreshAction),
        GestureType.Menu to idOf(BrowserAction.ShowMenuDialog),
        // ToggleTouchPagination was a duplicate of ToggleTouchTurnPage and has
        // been removed from the catalog — remap legacy bindings to the survivor.
        GestureType.TouchPagination to idOf(BrowserAction.ToggleTouchTurnPage),
        GestureType.KeyPageUp to idOf(BrowserAction.SendPageUpKey),
        GestureType.KeyPageDown to idOf(BrowserAction.SendPageDownKey),
        GestureType.KeyLeft to idOf(BrowserAction.SendLeftKey),
        GestureType.KeyRight to idOf(BrowserAction.SendRightKey),
        GestureType.InputUrl to idOf(BrowserAction.FocusOnInput),
    )
}
