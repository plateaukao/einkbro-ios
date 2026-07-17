package info.plateaukao.einkbro.view.handlers

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.util.PlatformActions
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.dialog.compose.MenuItemType

/**
 * Maps main-menu taps and long-presses to [BrowserAction]s. Port of Android's
 * MenuActionHandler. (Android's Quit item is dropped on iOS — apps cannot
 * self-terminate, PARITY_PLAN §7.)
 */
class MenuActionHandler(
    private val dispatch: (BrowserAction) -> Unit,
    private val currentUrl: () -> String,
) {
    private val config = AppServices.config

    fun handleLongClick(menuItemType: MenuItemType) = when (menuItemType) {
        MenuItemType.Translate -> dispatch(BrowserAction.ShowTranslationConfigDialog(true))
        MenuItemType.ReceiveData -> dispatch(BrowserAction.ToggleReceiveTextSearch)
        MenuItemType.SendLink -> dispatch(BrowserAction.ToggleTextSearch)
        MenuItemType.ShareLink -> dispatch(BrowserAction.ShareLinkLongPress)
        MenuItemType.TouchSetting -> dispatch(BrowserAction.ToggleTouchPagination)
        MenuItemType.BoldFont -> dispatch(BrowserAction.ShowFontBoldnessDialog)
        MenuItemType.ReaderMode -> dispatch(BrowserAction.ShowReaderSettingsDialog)
        MenuItemType.Settings -> dispatch(BrowserAction.OpenSettings)
        MenuItemType.Tts -> dispatch(BrowserAction.ShowTtsSettingsDialog)
        MenuItemType.Instapaper -> dispatch(BrowserAction.ConfigureInstapaper)
        MenuItemType.SaveArchive -> dispatch(BrowserAction.ShowSavedPages)
        else -> Unit
    }

    fun handle(menuItemType: MenuItemType) = when (menuItemType) {
        MenuItemType.Tts -> dispatch(BrowserAction.HandleTtsButton)
        MenuItemType.QuickToggle -> dispatch(BrowserAction.ShowFastToggleDialog)
        MenuItemType.OpenHome -> dispatch(BrowserAction.UpdateAlbum(config.favoriteUrl))
        MenuItemType.CloseTab -> dispatch(BrowserAction.RemoveAlbum)

        MenuItemType.SplitScreen -> dispatch(BrowserAction.ToggleSplitScreen())
        MenuItemType.Translate -> dispatch(BrowserAction.ShowTranslation)
        MenuItemType.VerticalRead -> dispatch(BrowserAction.ToggleVerticalRead)
        MenuItemType.ReaderMode -> dispatch(BrowserAction.ToggleReaderMode)
        MenuItemType.TouchSetting -> dispatch(BrowserAction.ShowTouchAreaDialog)
        MenuItemType.ToolbarSetting -> dispatch(BrowserAction.ShowToolbarConfigDialog)

        MenuItemType.ReceiveData -> dispatch(BrowserAction.ToggleReceiveLink)
        MenuItemType.SendLink -> dispatch(BrowserAction.SendToRemote(currentUrl()))

        MenuItemType.ShareLink -> dispatch(BrowserAction.ShareLink)
        MenuItemType.OpenWith -> PlatformActions.openUrl(currentUrl())
        MenuItemType.Highlights -> dispatch(BrowserAction.ShowHighlights)
        MenuItemType.SetHome -> {
            config.favoriteUrl = currentUrl()
            EBToast.show(AppServices.context, "Home page set to current page")
        }

        MenuItemType.SaveBookmark -> dispatch(BrowserAction.SaveBookmark())
        MenuItemType.Epub -> dispatch(BrowserAction.ShowEpubDialog)
        MenuItemType.SavePdf -> dispatch(BrowserAction.SavePdf)

        MenuItemType.FontSize -> dispatch(BrowserAction.ShowFontSizeChangeDialog)
        MenuItemType.InvertColor -> dispatch(BrowserAction.InvertColors)

        MenuItemType.WhiteBknd -> dispatch(BrowserAction.ToggleWhiteBackground)
        MenuItemType.BoldFont -> dispatch(BrowserAction.ToggleBoldFont)
        MenuItemType.BlackFont -> dispatch(BrowserAction.ToggleBlackFont)
        MenuItemType.Search -> dispatch(BrowserAction.ShowSearchPanel)
        MenuItemType.Download -> dispatch(BrowserAction.ShowSavedPages)
        MenuItemType.SaveArchive -> dispatch(BrowserAction.SavePageForLater)
        MenuItemType.SaveMht -> dispatch(BrowserAction.SaveWebArchive)
        MenuItemType.Settings -> dispatch(BrowserAction.OpenSettings)

        MenuItemType.PageAiActions -> dispatch(BrowserAction.ShowPageAiActionMenu)
        MenuItemType.Instapaper -> dispatch(BrowserAction.AddToInstapaper)
        MenuItemType.AudioOnly -> dispatch(BrowserAction.ToggleAudioOnlyMode)
        MenuItemType.SiteSettings -> dispatch(BrowserAction.ShowSiteSettingsDialog)
    }
}
