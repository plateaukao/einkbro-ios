package info.plateaukao.einkbro.view.handlers

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.toolbaricons.ToolbarAction
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API

/**
 * Maps toolbar taps and long-presses to [BrowserAction]s. Port of Android's
 * ToolbarActionHandler; activity-only branches (new window, move-to-background)
 * are impossible on iOS and surface a toast instead (PARITY_PLAN §7).
 */
class ToolbarActionHandler(private val dispatch: (BrowserAction) -> Unit) {

    fun handleLongClick(toolbarAction: ToolbarAction) = when (toolbarAction) {
        ToolbarAction.Back -> dispatch(BrowserAction.OpenHistoryPage(6))
        ToolbarAction.BoldFont -> dispatch(BrowserAction.ShowFontBoldnessDialog)
        ToolbarAction.Bookmark -> dispatch(BrowserAction.SaveBookmark())
        ToolbarAction.Font -> dispatch(BrowserAction.ToggleReaderMode)
        ToolbarAction.NewTab ->
            EBToast.show(AppServices.context, "Multiple app windows aren't supported on iOS")
        ToolbarAction.PageDown -> dispatch(BrowserAction.JumpToBottom)
        ToolbarAction.PageInfo -> dispatch(BrowserAction.SummarizeContent)
        ToolbarAction.PageUp -> dispatch(BrowserAction.JumpToTop)
        ToolbarAction.ReaderMode -> dispatch(BrowserAction.ShowReaderSettingsDialog)
        ToolbarAction.Refresh -> dispatch(BrowserAction.ToggleFullscreen)
        ToolbarAction.Settings -> dispatch(BrowserAction.ShowFastToggleDialog)
        ToolbarAction.TabCount -> dispatch(BrowserAction.ToggleIncognitoMode)
        ToolbarAction.TranslateByParagraph ->
            dispatch(BrowserAction.ConfigureTranslationLanguage(TRANSLATE_API.GOOGLE))

        ToolbarAction.Translation -> dispatch(BrowserAction.ShowTranslationConfigDialog(true))
        ToolbarAction.Tts -> dispatch(BrowserAction.ShowTtsSettingsDialog)
        ToolbarAction.Touch -> dispatch(BrowserAction.ShowTouchAreaDialog)
        ToolbarAction.ChatWithWeb -> dispatch(BrowserAction.ChatWithWeb(useSplitScreen = true))
        ToolbarAction.ShareLink -> dispatch(BrowserAction.ShareLinkLongPress)
        ToolbarAction.Userscript -> dispatch(BrowserAction.OpenUserScriptManager)
        else -> {}
    }

    fun handleClick(toolbarAction: ToolbarAction) = when (toolbarAction) {
        ToolbarAction.Back -> dispatch(BrowserAction.HandleBackKey)
        ToolbarAction.BoldFont -> dispatch(BrowserAction.ToggleBoldFont)
        ToolbarAction.Bookmark -> dispatch(BrowserAction.OpenBookmarkPage)
        ToolbarAction.CloseTab -> dispatch(BrowserAction.RemoveAlbum)
        ToolbarAction.DecreaseFont -> dispatch(BrowserAction.DecreaseFontSize)
        ToolbarAction.Desktop -> dispatch(BrowserAction.ToggleDesktopMode)
        ToolbarAction.DuplicateTab -> dispatch(BrowserAction.DuplicateTab)
        ToolbarAction.Font -> dispatch(BrowserAction.ShowFontSizeChangeDialog)
        ToolbarAction.Forward -> dispatch(BrowserAction.GoForward)
        ToolbarAction.FullScreen -> dispatch(BrowserAction.ToggleFullscreen)
        ToolbarAction.GoogleInPlace ->
            dispatch(BrowserAction.Translate(TranslationMode.GOOGLE_IN_PLACE))
        ToolbarAction.IconSetting -> dispatch(BrowserAction.ShowToolbarConfigDialog)
        ToolbarAction.IncreaseFont -> dispatch(BrowserAction.IncreaseFontSize)
        ToolbarAction.InputUrl -> dispatch(BrowserAction.FocusOnInput)
        ToolbarAction.MoveToBackground ->
            EBToast.show(AppServices.context, "iOS apps can't send themselves to the background")
        ToolbarAction.NewTab -> dispatch(BrowserAction.NewATab)
        ToolbarAction.PageDown -> dispatch(BrowserAction.PageDown)
        ToolbarAction.PageInfo -> {}
        ToolbarAction.PageUp -> dispatch(BrowserAction.PageUp)
        ToolbarAction.ReaderMode -> dispatch(BrowserAction.ToggleReaderMode)
        ToolbarAction.Refresh -> dispatch(BrowserAction.RefreshAction)
        ToolbarAction.RotateScreen -> dispatch(BrowserAction.RotateScreen)
        ToolbarAction.Search -> dispatch(BrowserAction.ShowSearchPanel)
        ToolbarAction.Settings -> dispatch(BrowserAction.ShowMenuDialog)
        ToolbarAction.Spacer1 -> {}
        ToolbarAction.Spacer2 -> {}
        ToolbarAction.TabCount -> dispatch(BrowserAction.ShowOverview)
        ToolbarAction.TOC -> dispatch(BrowserAction.ShowTocDialog)
        ToolbarAction.Tts -> dispatch(BrowserAction.HandleTtsButton)
        ToolbarAction.Time -> {}
        ToolbarAction.Title -> dispatch(BrowserAction.FocusOnInput)
        ToolbarAction.Touch -> dispatch(BrowserAction.ToggleTouchTurnPage)
        ToolbarAction.TouchDirectionLeftRight -> dispatch(BrowserAction.ToggleSwitchTouchAreaAction)
        ToolbarAction.TouchDirectionUpDown -> dispatch(BrowserAction.ToggleSwitchTouchAreaAction)
        ToolbarAction.Translation -> dispatch(BrowserAction.ShowTranslation)
        ToolbarAction.TranslateByParagraph ->
            dispatch(BrowserAction.Translate(TranslationMode.TRANSLATE_BY_PARAGRAPH))
        ToolbarAction.VerticalLayout -> dispatch(BrowserAction.ToggleVerticalRead)
        ToolbarAction.SaveEpub -> dispatch(BrowserAction.ShowEpubDialog)
        ToolbarAction.ShareLink -> dispatch(BrowserAction.ShareLink)
        ToolbarAction.InvertColor -> dispatch(BrowserAction.InvertColors)
        ToolbarAction.ChatWithWeb -> dispatch(BrowserAction.ChatWithWeb())
        ToolbarAction.PageAi -> dispatch(BrowserAction.ShowPageAiActionMenu)
        ToolbarAction.AudioOnly -> dispatch(BrowserAction.ToggleAudioOnlyMode)
        ToolbarAction.Userscript -> dispatch(BrowserAction.ShowUserScriptCommands)
    }
}
