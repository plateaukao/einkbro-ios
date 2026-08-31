package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.Feed
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CancelPresentation
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.InvertColorsOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.HeadsetOff
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import info.plateaukao.einkbro.util.screenWidthDp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.BuildConfig
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.view.compose.MyTheme

@Composable
fun MenuDialogContent(
    url: String = "",
    isSpeaking: Boolean = false,
    isAudioOnly: Boolean = false,
    hasVideo: Boolean = false,
    isTouchPaginationEnabled: Boolean = false,
    itemClicked: (MenuItemType) -> Unit = {},
    itemLongClicked: (MenuItemType) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val hiddenItems = remember {
        config.ui.hiddenMenuItems.mapNotNull { name ->
            runCatching { MenuItemType.valueOf(name) }.getOrNull()
        }.toSet()
    }
    val menuItemOrder = remember { config.ui.menuItemOrder }
    CompositionLocalProvider(
        LocalMenuHideConfig provides MenuHideConfig(hideMode = false, hiddenItems = hiddenItems)
    ) {
        MenuItems(
            hasWhiteBkd = config.whiteBackground(url),
            boldFont = config.display.boldFontStyle,
            blackFont = config.display.blackFontStyle,
            isSpeaking = isSpeaking,
            isAudioOnly = isAudioOnly,
            hasVideo = hasVideo,
            hasInvertedColor = config.hasInvertedColor(url),
            isTouchPaginationEnabled = isTouchPaginationEnabled,
            onClicked = { onDismiss(); itemClicked(it) },
            onLongClicked = { itemLongClicked(it); onDismiss() },
            menuItemOrder = menuItemOrder,
        )
    }
}


enum class MenuItemType {
    // Android also has Quit; dropped on iOS — apps cannot self-terminate
    // (PARITY_PLAN §7). CloseTab is dropped for the same reason: on Android it
    // doubles as "quit" once the last tab goes, while here it can only ever
    // hand back a fresh home tab. Tabs are closed from the tab list instead.
    Tts, QuickToggle, OpenHome,
    SplitScreen, Translate, VerticalRead, ReaderMode, TouchSetting, ToolbarSetting,
    // Android also has Shortcut (add-to-home-screen); dropped on iOS — no API
    // for per-site home icons (PARITY_PLAN §7).
    ReceiveData, SendLink, ShareLink, OpenWith,
    SetHome, SaveBookmark, Epub, SavePdf,
    FontSize, WhiteBknd, BoldFont, Search, Download, Settings, BlackFont,
    SaveArchive, SaveMht, Highlights, InvertColor, PageAiActions, Instapaper, AudioOnly,
    SiteSettings
}

enum class MenuSection(val headerRes: StringResource?) {
    Top(null),
    Share(Res.string.share_save),
    Content(Res.string.content_adjustment),
    Bottom(null),
}

// Flat sequence of items with Boundary markers dividing sections. Boundaries stay fixed
// in relative order; dragging an Item past a Boundary moves it into the other section.
// Spacer is display-only (empty grid cell used to pad the overflow row in bottom-first layouts).
sealed class MenuEntry {
    data class Item(val type: MenuItemType) : MenuEntry()
    data class Boundary(val sectionStart: MenuSection) : MenuEntry()
    object Spacer : MenuEntry()
}

/**
 * Gated items stay out of the menu grid — and out of the hide/reorder editor.
 * LAN link / app-data sharing rides on the same runtime unlock as the Backup
 * screen (einkbro://googlesync; both need the multicast entitlement Apple has
 * not granted for this team); Instapaper has its own build flag (it is inert
 * without account credentials).
 */
private val MenuItemType.isEnabled: Boolean
    get() = when (this) {
        MenuItemType.SendLink, MenuItemType.ReceiveData -> AppServices.config.isBackupRestoreUnlocked
        MenuItemType.Instapaper -> BuildConfig.INSTAPAPER_ENABLED
        else -> true
    }

private val defaultSectionItems: Map<MenuSection, List<MenuItemType>> = mapOf(
    MenuSection.Top to listOf(
        MenuItemType.Highlights, MenuItemType.SetHome, MenuItemType.OpenHome,
    ),
    MenuSection.Share to listOf(
        MenuItemType.ReceiveData, MenuItemType.SaveBookmark,
        MenuItemType.OpenWith, MenuItemType.ShareLink,
        MenuItemType.SendLink, MenuItemType.Instapaper, MenuItemType.SaveArchive,
        MenuItemType.SaveMht, MenuItemType.Epub, MenuItemType.SavePdf,
    ),
    MenuSection.Content to listOf(
        MenuItemType.PageAiActions, MenuItemType.SplitScreen, MenuItemType.Translate,
        MenuItemType.VerticalRead, MenuItemType.ReaderMode, MenuItemType.TouchSetting,
        MenuItemType.Tts, MenuItemType.InvertColor, MenuItemType.WhiteBknd,
        MenuItemType.BlackFont, MenuItemType.BoldFont, MenuItemType.FontSize,
    ),
    MenuSection.Bottom to listOf(
        MenuItemType.AudioOnly, MenuItemType.Search, MenuItemType.Download,
        MenuItemType.ToolbarSetting, MenuItemType.QuickToggle, MenuItemType.SiteSettings,
        MenuItemType.Settings,
    ),
).mapValues { (_, items) -> items.filter { it.isEnabled } }

val defaultMenuEntries: List<MenuEntry> = buildList {
    addAll(defaultSectionItems[MenuSection.Top]!!.map { MenuEntry.Item(it) })
    add(MenuEntry.Boundary(MenuSection.Share))
    addAll(defaultSectionItems[MenuSection.Share]!!.map { MenuEntry.Item(it) })
    add(MenuEntry.Boundary(MenuSection.Content))
    addAll(defaultSectionItems[MenuSection.Content]!!.map { MenuEntry.Item(it) })
    add(MenuEntry.Boundary(MenuSection.Bottom))
    addAll(defaultSectionItems[MenuSection.Bottom]!!.map { MenuEntry.Item(it) })
}

private const val BOUNDARY_PREFIX = "#"

// Spacers are display-only; never persisted.
fun encodeMenuEntries(entries: List<MenuEntry>): List<String> = entries.mapNotNull { e ->
    when (e) {
        is MenuEntry.Item -> e.type.name
        is MenuEntry.Boundary -> BOUNDARY_PREFIX + e.sectionStart.name
        is MenuEntry.Spacer -> null
    }
}

private fun decodeMenuEntries(tokens: List<String>): List<MenuEntry> = tokens.mapNotNull { token ->
    if (token.startsWith(BOUNDARY_PREFIX)) {
        val name = token.removePrefix(BOUNDARY_PREFIX)
        runCatching { MenuSection.valueOf(name) }.getOrNull()?.let { MenuEntry.Boundary(it) }
    } else {
        // Drops disabled items from an order persisted by an earlier build; when the
        // flag flips back on, effectiveMenuEntries re-appends them to their section.
        runCatching { MenuItemType.valueOf(token) }.getOrNull()
            ?.takeIf { it.isEnabled }
            ?.let { MenuEntry.Item(it) }
    }
}

/**
 * Transforms underlying entries into display entries with bottom-first layout:
 * within each section, the last chunk (partial overflow) is moved to the top, preceded
 * by Spacer entries so the top row's leftmost cells are empty.
 */
fun menuDisplayEntries(underlying: List<MenuEntry>, cols: Int = MENU_GRID_COLUMNS): List<MenuEntry> {
    val out = mutableListOf<MenuEntry>()
    val buf = mutableListOf<MenuEntry.Item>()

    fun flush() {
        if (buf.isEmpty()) return
        val chunks = buf.chunked(cols)
        val reversed = chunks.asReversed()
        val topChunk = reversed.first()
        val padding = cols - topChunk.size
        if (padding > 0) repeat(padding) { out.add(MenuEntry.Spacer) }
        out.addAll(topChunk)
        reversed.drop(1).forEach { chunk -> out.addAll(chunk) }
        buf.clear()
    }

    underlying.forEach { e ->
        when (e) {
            is MenuEntry.Item -> buf.add(e)
            is MenuEntry.Boundary -> { flush(); out.add(e) }
            is MenuEntry.Spacer -> Unit
        }
    }
    flush()
    return out
}

/**
 * Inverse of [menuDisplayEntries]: turns a display list (possibly edited by the user's drag)
 * back into underlying Item + Boundary entries. Spacers are dropped; per-section chunks are
 * reversed so the section is stored in natural top-down order.
 */
fun menuDisplayToUnderlying(display: List<MenuEntry>, cols: Int = MENU_GRID_COLUMNS): List<MenuEntry> {
    val out = mutableListOf<MenuEntry>()
    val buf = mutableListOf<MenuEntry>()

    fun flush() {
        if (buf.isEmpty()) return
        val chunks = buf.chunked(cols)
        chunks.asReversed().forEach { chunk ->
            chunk.forEach { e -> if (e is MenuEntry.Item) out.add(e) }
        }
        buf.clear()
    }

    display.forEach { e ->
        when (e) {
            is MenuEntry.Boundary -> { flush(); out.add(e) }
            else -> buf.add(e)
        }
    }
    flush()
    return out
}

// Resolve effective entries: stored order first, then append any items & missing boundaries
// from defaults so newly-added items show up (appended to their default section).
fun effectiveMenuEntries(stored: List<String>): List<MenuEntry> {
    val storedEntries = decodeMenuEntries(stored)
    // Fall back to defaults if nothing is stored, or if the stored data predates Boundary tokens.
    if (storedEntries.isEmpty() || storedEntries.none { it is MenuEntry.Boundary }) return defaultMenuEntries
    val presentItems = storedEntries.filterIsInstance<MenuEntry.Item>().map { it.type }.toSet()
    val presentBoundaries = storedEntries.filterIsInstance<MenuEntry.Boundary>().map { it.sectionStart }.toSet()

    // Ensure every boundary exists; if a boundary is missing, add it at the corresponding
    // default position relative to existing items.
    val result = storedEntries.toMutableList()
    MenuSection.entries.drop(1).forEach { section ->
        if (section !in presentBoundaries) result.add(MenuEntry.Boundary(section))
    }
    // Append missing items at the end of their default section if possible, else at end.
    val missing = defaultMenuEntries.filterIsInstance<MenuEntry.Item>()
        .map { it.type }.filter { it !in presentItems }
    missing.forEach { type ->
        val defaultSection = defaultSectionItems.entries.first { type in it.value }.key
        val insertIdx = findSectionEnd(result, defaultSection)
        result.add(insertIdx, MenuEntry.Item(type))
    }
    return result
}

private fun findSectionEnd(entries: List<MenuEntry>, section: MenuSection): Int {
    // Section starts at the Boundary for it (or index 0 for Top) and ends just before the next Boundary.
    val start = if (section == MenuSection.Top) 0
    else entries.indexOfFirst { it is MenuEntry.Boundary && it.sectionStart == section }.let { if (it < 0) return entries.size else it + 1 }
    val end = entries.drop(start).indexOfFirst { it is MenuEntry.Boundary }
    return if (end < 0) entries.size else start + end
}

const val MENU_GRID_COLUMNS = 6

data class MenuHideConfig(
    val hideMode: Boolean = false,
    val reorderMode: Boolean = false,
    val hiddenItems: Set<MenuItemType> = emptySet(),
    val onToggleHide: (MenuItemType) -> Unit = {},
)

val LocalMenuHideConfig = staticCompositionLocalOf { MenuHideConfig() }

data class MenuActions(
    val onClicked: (MenuItemType) -> Unit = {},
    val onLongClicked: (MenuItemType) -> Unit = {},
)

val LocalMenuActions = staticCompositionLocalOf { MenuActions() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HideableSlot(type: MenuItemType, content: @Composable () -> Unit) {
    val cfg = LocalMenuHideConfig.current
    val isHidden = type in cfg.hiddenItems
    when {
        // Reorder mode: render grayed but don't intercept clicks (drag handle on the item handles input).
        cfg.reorderMode -> Box(modifier = Modifier.alpha(if (isHidden) 0.3f else 1f)) { content() }
        cfg.hideMode -> {
            Box(modifier = Modifier.alpha(if (isHidden) 0.3f else 1f)) {
                content()
                // Overlay absorbs both tap and long-press so underlying MenuItem handlers don't fire.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onLongClick = { cfg.onToggleHide(type) },
                            onClick = { cfg.onToggleHide(type) },
                        )
                )
            }
        }
        isHidden -> Unit
        else -> content()
    }
}

@Composable
private fun HideableMenuItem(
    type: MenuItemType,
    titleResId: StringResource,
    imageVector: ImageVector? = null,
    iconResId: DrawableResource? = null,
    supportsLongClick: Boolean = false,
) {
    val actions = LocalMenuActions.current
    HideableSlot(type) {
        MenuItem(
            titleResId = titleResId,
            iconResId = iconResId,
            imageVector = imageVector,
            onLongClicked = if (supportsLongClick) ({ actions.onLongClicked(type) }) else ({}),
            onClicked = { actions.onClicked(type) },
        )
    }
}

/**
 * Renders a HideableMenuItem for a given MenuItemType, applying any state-dependent icon.
 * Centralizing this lets row rendering be data-driven (iterate over an ordered List<MenuItemType>).
 */
@Composable
fun MenuItemForType(
    type: MenuItemType,
    hasWhiteBkd: Boolean = false,
    boldFont: Boolean = false,
    blackFont: Boolean = false,
    isSpeaking: Boolean = false,
    isAudioOnly: Boolean = false,
    hasInvertedColor: Boolean = false,
    isTouchPaginationEnabled: Boolean = false,
) {
    when (type) {
        MenuItemType.Highlights -> HideableMenuItem(type, Res.string.menu_highlights, Icons.Outlined.EditNote)
        MenuItemType.SetHome -> HideableMenuItem(type, Res.string.menu_fav, Icons.Outlined.AddHome)
        MenuItemType.OpenHome -> HideableMenuItem(type, Res.string.menu_openFav, Icons.Outlined.Home)
        MenuItemType.ReceiveData -> HideableMenuItem(type, Res.string.menu_receive, Icons.Outlined.InstallMobile, supportsLongClick = true)
        MenuItemType.SaveBookmark -> HideableMenuItem(type, Res.string.menu_save_bookmark, Icons.Outlined.BookmarkAdd)
        MenuItemType.OpenWith -> HideableMenuItem(type, Res.string.menu_open_with, Icons.Outlined.Apps)
        MenuItemType.ShareLink -> HideableMenuItem(type, Res.string.menu_share_link, Icons.Outlined.Share, supportsLongClick = true)
        MenuItemType.SendLink -> HideableMenuItem(type, Res.string.menu_send_link, Icons.AutoMirrored.Outlined.SendToMobile, supportsLongClick = true)
        MenuItemType.Instapaper -> HideableMenuItem(type, Res.string.menu_instapaper, Icons.Outlined.CloudUpload, supportsLongClick = true)
        MenuItemType.SaveArchive -> HideableMenuItem(type, Res.string.menu_save_archive, Icons.Outlined.Save, supportsLongClick = true)
        MenuItemType.SaveMht -> HideableMenuItem(type, Res.string.menu_save_mht, Icons.Outlined.Save)
        MenuItemType.Epub -> HideableMenuItem(type, Res.string.menu_epub, Icons.AutoMirrored.Outlined.Article)
        MenuItemType.SavePdf -> HideableMenuItem(type, Res.string.menu_save_pdf, Icons.Outlined.PictureAsPdf)
        MenuItemType.PageAiActions -> HideableMenuItem(type, Res.string.page_ai, iconResId = Res.drawable.ic_robot)
        MenuItemType.SplitScreen -> HideableMenuItem(type, Res.string.split_screen, Icons.Outlined.ViewStream)
        MenuItemType.Translate -> HideableMenuItem(type, Res.string.translate, Icons.Outlined.Translate, supportsLongClick = true)
        MenuItemType.VerticalRead -> HideableMenuItem(type, Res.string.vertical_read, Icons.Outlined.ViewColumn)
        MenuItemType.ReaderMode -> HideableMenuItem(type, Res.string.reader_mode, Icons.AutoMirrored.Outlined.ChromeReaderMode, supportsLongClick = true)
        MenuItemType.TouchSetting -> {
            val touchRes = if (isTouchPaginationEnabled) Res.drawable.ic_touch_enabled else Res.drawable.ic_touch_disabled
            HideableMenuItem(type, Res.string.touch_area_setting, iconResId = touchRes, supportsLongClick = true)
        }
        MenuItemType.Tts -> {
            val ttsRes = if (isSpeaking) Icons.Filled.RecordVoiceOver else Icons.Outlined.RecordVoiceOver
            HideableMenuItem(type, Res.string.menu_tts, ttsRes, supportsLongClick = true)
        }
        MenuItemType.InvertColor -> {
            val invertRes = if (hasInvertedColor) Icons.Outlined.InvertColorsOff else Icons.Outlined.InvertColors
            HideableMenuItem(type, Res.string.menu_invert_color, invertRes)
        }
        MenuItemType.WhiteBknd -> {
            val whiteRes = if (hasWhiteBkd) Res.drawable.ic_white_background_active else Res.drawable.ic_white_background
            HideableMenuItem(type, Res.string.white_background, iconResId = whiteRes)
        }
        MenuItemType.BlackFont -> {
            val blackRes = if (blackFont) Icons.TwoTone.Copyright else Icons.Outlined.Copyright
            HideableMenuItem(type, Res.string.black_font, blackRes)
        }
        MenuItemType.BoldFont -> {
            val boldRes = if (boldFont) Res.drawable.ic_bold_font_active else Res.drawable.ic_bold_font
            HideableMenuItem(type, Res.string.bold_font, iconResId = boldRes, supportsLongClick = true)
        }
        MenuItemType.FontSize -> HideableMenuItem(type, Res.string.font_size, Icons.Outlined.FormatSize)
        MenuItemType.AudioOnly -> {
            val audioOnlyIcon = if (isAudioOnly) Icons.Outlined.Headset else Icons.Outlined.HeadsetOff
            HideableMenuItem(type, Res.string.audio_only_mode, audioOnlyIcon)
        }
        MenuItemType.Search -> HideableMenuItem(type, Res.string.menu_other_searchSite, Icons.Outlined.Search)
        MenuItemType.Download -> HideableMenuItem(type, Res.string.menu_download, Icons.Outlined.Download)
        MenuItemType.ToolbarSetting -> HideableMenuItem(type, Res.string.toolbar_icons, Icons.Outlined.Straighten)
        MenuItemType.QuickToggle -> HideableMenuItem(type, Res.string.menu_quickToggle, Icons.Outlined.SettingsSuggest)
        MenuItemType.SiteSettings -> HideableMenuItem(type, Res.string.site_settings, Icons.Outlined.Tune)
        MenuItemType.Settings -> HideableMenuItem(type, Res.string.settings, Icons.Outlined.Settings, supportsLongClick = true)
    }
}

@Composable
fun MenuItems(
    hasWhiteBkd: Boolean,
    boldFont: Boolean,
    blackFont: Boolean,
    isSpeaking: Boolean,
    isAudioOnly: Boolean,
    hasVideo: Boolean,
    hasInvertedColor: Boolean,
    isTouchPaginationEnabled: Boolean,
    onClicked: (MenuItemType) -> Unit,
    onLongClicked: (MenuItemType) -> Unit,
    menuItemOrder: List<String> = emptyList(),
) {
    val hiddenItems = LocalMenuHideConfig.current.hiddenItems
    val hideMode = LocalMenuHideConfig.current.hideMode
    val entries = effectiveMenuEntries(menuItemOrder)

    // Partition into sections by scanning for Boundary markers.
    data class Section(val section: MenuSection, val items: List<MenuItemType>)
    val sections = buildList {
        var current = MenuSection.Top
        val buf = mutableListOf<MenuItemType>()
        fun flush() { add(Section(current, buf.toList())); buf.clear() }
        entries.forEach { e ->
            when (e) {
                is MenuEntry.Item -> buf.add(e.type)
                is MenuEntry.Boundary -> { flush(); current = e.sectionStart }
                is MenuEntry.Spacer -> Unit
            }
        }
        flush()
    }

    // Apply hide + hasVideo filtering per section.
    val sectionsFiltered = sections.map { sec ->
        val filtered = sec.items
            .let { if (hasVideo) it else it.filter { t -> t != MenuItemType.AudioOnly } }
            .let { if (hideMode) it else it.filter { t -> t !in hiddenItems } }
        sec.copy(items = filtered)
    }

    CompositionLocalProvider(
        LocalMenuActions provides MenuActions(onClicked, onLongClicked)
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.End,
        ) {
            val renderItem: @Composable (MenuItemType) -> Unit = { type ->
                MenuItemForType(
                    type = type,
                    hasWhiteBkd = hasWhiteBkd,
                    boldFont = boldFont,
                    blackFont = blackFont,
                    isSpeaking = isSpeaking,
                    isAudioOnly = isAudioOnly,
                    hasInvertedColor = hasInvertedColor,
                    isTouchPaginationEnabled = isTouchPaginationEnabled,
                )
            }
            var renderedAnySection = false
            sectionsFiltered.forEach { sec ->
                if (sec.items.isEmpty()) return@forEach
                if (renderedAnySection) HorizontalSeparator()
                // Reverse the chunk order so the last chunk (full row) ends up visually at
                // the bottom and any partial chunk (overflow) ends up at the top. For 11 items
                // with 6 columns, this renders as 5 items on top and 6 on bottom instead of
                // the default 6 on top and 5 on bottom.
                sec.items.chunked(MENU_GRID_COLUMNS).asReversed().forEach { rowItems ->
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        rowItems.forEach { renderItem(it) }
                    }
                }
                renderedAnySection = true
            }
        }
    }
}

@Composable
fun MenuItem(
    titleResId: StringResource,
    imageVector: ImageVector,
    isLargeType: Boolean = false,
    showIcon: Boolean = true,
    onLongClicked: () -> Unit = {},
    onClicked: () -> Unit = {},
) {
    MenuItem(
        titleResId,
        null,
        imageVector,
        isLargeType,
        showIcon,
        onLongClicked,
        onClicked,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuItem(
    titleResId: StringResource,
    iconResId: DrawableResource?,
    imageVector: ImageVector? = null,
    isLargeType: Boolean = false,
    showIcon: Boolean = true,
    onLongClicked: () -> Unit = {},
    onClicked: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val screenWidth = screenWidthDp()
    // Wide enough for the longest single English word in a label ("Instapaper",
    // "Downloads", "background") to sit on one line — narrower cells broke them
    // mid-word, which reads far worse than a two-line label. Capped so the
    // six-column grid still fits a 320pt screen (deployment target is iOS 15).
    val maxCellWidth = ((screenWidth - 24) / MENU_GRID_COLUMNS).coerceAtLeast(40)
    val width = when {
        isLargeType -> if (screenWidth > 500) 76 else 64
        screenWidth > 500 -> 68
        else -> 58
    }.coerceAtMost(maxCellWidth).dp

    val fontSize = if (!showIcon) 16.sp else if (screenWidth > 500) 10.sp else 8.sp

    // In reorder mode we hand gesture control to the outer ReorderableItem's
    // longPressDraggableHandle — combinedClickable here would consume long-press first.
    val reorderMode = LocalMenuHideConfig.current.reorderMode

    Box {
        if (pressed) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colors.onBackground, shape = CircleShape)
                    .align(Alignment.TopCenter)
            )
        }
        Column(
            modifier = Modifier
                .width(width)
                .height(if (!showIcon) 50.dp else if (isLargeType) 80.dp else 70.dp)
                .then(
                    if (reorderMode) Modifier
                    else Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onLongClick = { onLongClicked() },
                        onClick = { onClicked() },
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (!showIcon) Arrangement.Center else Arrangement.Top
        ) {
            if (showIcon) {
                if (imageVector != null) {
                    Icon(
                        imageVector = imageVector, contentDescription = null,
                        modifier = Modifier
                            .size(if (isLargeType) 55.dp else 44.dp)
                            .padding(horizontal = 6.dp),
                        tint = MaterialTheme.colors.onBackground
                    )
                } else if (iconResId != null) {
                    Icon(
                        imageVector = vectorResource(iconResId), contentDescription = null,
                        modifier = Modifier
                            .size(if (isLargeType) 55.dp else 44.dp)
                            .padding(horizontal = 6.dp),
                        tint = MaterialTheme.colors.onBackground
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = if (showIcon) (-5).dp else 0.dp),
                    text = stringResource(titleResId),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = if (!showIcon) 20.sp else 12.sp,
                    fontSize = fontSize,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    }
}

@Composable
private fun PreviewItem() {
    MyTheme {
        Column {
            MenuItem(Res.string.title_appData, Icons.Outlined.Backup, showIcon = false) {}
            MenuItem(Res.string.title, null, Icons.Outlined.Translate) {}
        }
    }
}

@Composable
private fun PreviewMenuItems() {
    MyTheme {
        MenuItems(
            hasWhiteBkd = false,
            boldFont = false,
            blackFont = false,
            isSpeaking = false,
            isAudioOnly = false,
            hasVideo = false,
            hasInvertedColor = false,
            isTouchPaginationEnabled = false,
            onClicked = {},
            onLongClicked = {},
        )
    }
}
