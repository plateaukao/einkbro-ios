package info.plateaukao.einkbro.view.toolbaricons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CancelPresentation
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.outlined.ModeEdit
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

enum class ToolbarAction(
    val iconResId: DrawableResource? = null,
    val imageVector: ImageVector? = null,
    val titleResId: StringResource,
    val iconActiveInfo: IconActiveInfo = IconActiveInfo(isActivable = false),
    val isAddable: Boolean = true,
) {
    Title(imageVector = Icons.Outlined.Info, titleResId = Res.string.toolbar_title), // 0
    Back(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft, titleResId = Res.string.back),
    Refresh(
        imageVector = Icons.Outlined.Refresh,
        titleResId = Res.string.refresh,
        iconActiveInfo = IconActiveInfo(true, Res.drawable.ic_stop, Res.drawable.icon_refresh)
    ),
    Touch(
        imageVector = Icons.Outlined.TouchApp,
        titleResId = Res.string.touch_turn_page,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_touch_enabled,
            Res.drawable.ic_touch_disabled
        )
    ),
    PageUp(imageVector = Icons.Outlined.Upload, titleResId = Res.string.page_up),
    PageDown(imageVector = Icons.Outlined.Download, titleResId = Res.string.page_down),
    TabCount(imageVector = Icons.Outlined.LooksOne, titleResId = Res.string.tab_preview),
    Font(imageVector = Icons.Outlined.FormatSize, titleResId = Res.string.font_size),
    Settings(imageVector = Icons.Outlined.Menu, titleResId = Res.string.settings),
    Bookmark(imageVector = Icons.Outlined.Bookmarks, titleResId = Res.string.bookmarks),
    IconSetting(imageVector = Icons.Outlined.Straighten, titleResId = Res.string.toolbars),
    VerticalLayout(imageVector = Icons.Outlined.ViewColumn, titleResId = Res.string.vertical_read),
    ReaderMode(imageVector = Icons.AutoMirrored.Outlined.ChromeReaderMode, titleResId = Res.string.reader_mode),
    BoldFont(
        iconResId = Res.drawable.ic_bold_font,
        titleResId = Res.string.bold_font,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_bold_font_active,
            Res.drawable.ic_bold_font
        )
    ),
    IncreaseFont(imageVector = Icons.Outlined.TextIncrease, titleResId = Res.string.font_size_increase),
    DecreaseFont(imageVector = Icons.Outlined.TextDecrease, titleResId = Res.string.font_size_decrease),
    FullScreen(imageVector = Icons.Outlined.Fullscreen, titleResId = Res.string.fullscreen),
    Forward(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, titleResId = Res.string.forward),
    RotateScreen(imageVector = Icons.AutoMirrored.Outlined.RotateRight, titleResId = Res.string.rotate),
    Translation(imageVector = Icons.Outlined.Translate, titleResId = Res.string.translate),
    CloseTab(imageVector = Icons.Outlined.CancelPresentation, titleResId = Res.string.close_tab),
    InputUrl(imageVector = Icons.Outlined.ModeEdit, titleResId = Res.string.input_url),
    NewTab(imageVector = Icons.Outlined.LibraryAdd, titleResId = Res.string.open_new_tab),
    Desktop(
        imageVector = Icons.Outlined.DesktopWindows,
        titleResId = Res.string.desktop_mode,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.icon_desktop_activate,
            Res.drawable.icon_desktop
        )
    ),
    TOC(imageVector = Icons.AutoMirrored.Outlined.Toc, titleResId = Res.string.title_in_toc, isAddable = false),
    Search(imageVector = Icons.Outlined.Search, titleResId = Res.string.setting_title_search),
    DuplicateTab(imageVector = Icons.Outlined.FolderCopy, titleResId = Res.string.duplicate_tab),
    Tts(
        imageVector = Icons.Outlined.RecordVoiceOver,
        titleResId = Res.string.menu_tts,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_tts,
            Res.drawable.ic_voice_off
        )
    ),
    PageInfo(iconResId = Res.drawable.ic_page_count, titleResId = Res.string.page_count),
    GoogleInPlace(
        imageVector = Icons.Outlined.GTranslate,
        titleResId = Res.string.google_in_place
    ),
    TranslateByParagraph(
        imageVector = Icons.AutoMirrored.Outlined.Segment,
        titleResId = Res.string.inter_translate
    ),
    MoveToBackground(
        imageVector = Icons.Outlined.Minimize,
        titleResId = Res.string.move_to_background
    ),
    TouchDirectionUpDown(
        imageVector = Icons.Outlined.SwipeVertical,
        titleResId = Res.string.switch_touch_area_action_short,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_touch_direction_up,
            Res.drawable.ic_touch_direction_down
        )
    ),
    TouchDirectionLeftRight(
        imageVector = Icons.Outlined.Swipe,
        titleResId = Res.string.switch_touch_area_action_short,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_touch_direction_left,
            Res.drawable.ic_touch_direction_right
        )
    ),
    Time(
        imageVector = Icons.Outlined.AccessTime,
        titleResId = Res.string.toolbar_time,
    ),
    Spacer1(
        imageVector = Icons.Outlined.SpaceBar,
        titleResId = Res.string.expand_space,
    ),
    Spacer2(
        imageVector = Icons.Outlined.SpaceBar,
        titleResId = Res.string.expand_space,
    ),
    ShareLink(
        imageVector = Icons.Outlined.Share,
        titleResId = Res.string.menu_share_link,
    ),
    SaveEpub(
        imageVector = Icons.AutoMirrored.Outlined.Article,
        titleResId = Res.string.menu_save_epub,
    ),
    InvertColor(
        imageVector = Icons.Outlined.InvertColors,
        titleResId = Res.string.menu_invert_color,
    ),
    ChatWithWeb(
        imageVector = Icons.AutoMirrored.Outlined.Chat,
        titleResId = Res.string.chat_with_web,
    ),
    PageAi(
        iconResId = Res.drawable.ic_robot,
        titleResId = Res.string.page_ai,
    ),
    AudioOnly(
        iconResId = Res.drawable.ic_audio_only_off,
        titleResId = Res.string.audio_only_mode,
        iconActiveInfo = IconActiveInfo(
            true,
            Res.drawable.ic_audio_only_on,
            Res.drawable.ic_audio_only_off
        )
    ),
    // New entries must be appended: ordinals are persisted in toolbar configs.
    Userscript(
        imageVector = Icons.Outlined.Extension,
        titleResId = Res.string.setting_title_userscripts,
    );


    companion object {
        fun fromOrdinal(value: Int) = entries[value]
        val defaultActionsForPhone: List<ToolbarAction> = listOf(
            NewTab,
            Touch,
            ReaderMode,
            Refresh,
            Back,
            Bookmark,
            TabCount,
            InputUrl,
            Settings,
        )
        val defaultActions: List<ToolbarAction> = listOf(
            Title,
            NewTab,
            Touch,
            ReaderMode,
            Refresh,
            Back,
            Bookmark,
            TabCount,
            Settings,
        )
        val defaultReaderActions: List<ToolbarAction> = listOf(
            RotateScreen,
            FullScreen,
            BoldFont,
            Font,
            Touch,
            TOC,
            PageInfo,
            Settings,
            CloseTab,
        )
    }

    fun getCurrentResId(state: Boolean): DrawableResource? =
        if (iconActiveInfo.isActivable) {
            if (state) iconActiveInfo.activeResId else iconActiveInfo.inactiveResId
        } else {
            iconResId
        }
}

data class IconActiveInfo(
    val isActivable: Boolean = false,
    val activeResId: DrawableResource? = null,
    val inactiveResId: DrawableResource? = null,
)

// Immutable so lists of it compare structurally: mutableStateOf can dedup
// no-op rebuilds and Compose strong skipping can skip unchanged icons.
data class ToolbarActionInfo(
    val toolbarAction: ToolbarAction,
    val state: Boolean = false,
) {
    fun getCurrentResId(): DrawableResource? = toolbarAction.getCurrentResId(state)
}
