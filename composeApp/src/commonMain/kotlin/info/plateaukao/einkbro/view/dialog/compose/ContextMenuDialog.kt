package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.TabUnselected
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.view.compose.MyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class MenuItemConfig(
    val type: ContextMenuItemType,
    val titleResId: StringResource,
    val imageVector: ImageVector? = null,
    val iconResId: DrawableResource? = null,
    val shouldShow: (url: String, shouldShowAdBlock: Boolean, shouldShowTranslateImage: Boolean) -> Boolean = { _, _, _ -> true }
)

data class MenuLayout(
    val firstRowItems: List<MenuItemConfig>,
    val secondRowItems: List<MenuItemConfig>
)

private fun createMenuLayout(isEbookMode: Boolean = false): MenuLayout {
    val firstRowItems = listOf(
        MenuItemConfig(
            ContextMenuItemType.NewTabForeground,
            Res.string.main_menu_new_tabOpen,
            Icons.Outlined.Tab
        ),
        MenuItemConfig(
            ContextMenuItemType.NewTabBackground,
            Res.string.main_menu_new_tab,
            Icons.Outlined.TabUnselected
        ),
        MenuItemConfig(
            ContextMenuItemType.OpenWith,
            Res.string.menu_open_with,
            Icons.Outlined.Apps
        ),
        MenuItemConfig(
            ContextMenuItemType.SplitScreen,
            Res.string.split_screen,
            Icons.Outlined.ViewStream
        ),
        MenuItemConfig(
            ContextMenuItemType.ShareLink,
            Res.string.menu_share_link,
            Icons.Outlined.Share
        )
    )

    val secondRowItems = listOfNotNull(
        if (isEbookMode) MenuItemConfig(
            ContextMenuItemType.GotoLink,
            Res.string.go_to,
            Icons.Outlined.Fingerprint
        ) else null,
        MenuItemConfig(
            ContextMenuItemType.SelectText,
            Res.string.text_select,
            Icons.AutoMirrored.Outlined.Segment
        ),
        MenuItemConfig(
            ContextMenuItemType.TranslateImage,
            Res.string.translate,
            iconResId = Res.drawable.ic_papago,
            shouldShow = { url, _, shouldShowTranslateImage ->
                shouldShowTranslateImage && (url.lowercase().contains("jpg") || url.lowercase().contains("png"))
            }
        ),
        MenuItemConfig(
            ContextMenuItemType.Tts,
            Res.string.menu_tts,
            Icons.Outlined.RecordVoiceOver
        ),
        MenuItemConfig(
            ContextMenuItemType.SaveAs,
            Res.string.menu_save_as,
            Icons.Outlined.Save
        ),
        MenuItemConfig(
            ContextMenuItemType.Summarize,
            Res.string.menu_summarize,
            Icons.AutoMirrored.Outlined.Chat
        )
    )

    return MenuLayout(firstRowItems, secondRowItems)
}

/**
 * Entry composable for the long-press link/image context menu. The Android
 * fragment also carried hover tracking (long-press drag selection mapped from
 * raw screen coordinates via View metrics) and window anchoring; both are
 * Android-View specific and dropped here — [hoveredItem] is still honored
 * when supplied so the hover indicator renders.
 */
@Composable
fun ContextMenuDialogContent(
    url: String = "https://github.com/plateaukao/einkbro",
    shouldShowAdBlock: Boolean = true,
    shouldShowTranslateImage: Boolean = false,
    isEbookMode: Boolean = false,
    hoveredItem: ContextMenuItemType? = null,
    itemClicked: (ContextMenuItemType) -> Unit = {},
    itemLongClicked: (ContextMenuItemType) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ContextMenuItems(
        url,
        shouldShowAdBlock,
        shouldShowTranslateImage,
        showIcons = AppServices.config.ui.showActionMenuIcons,
        isEbookMode = isEbookMode,
        hoveredItem = hoveredItem,
        onClicked = { item ->
            onDismiss()
            itemClicked(item)
        },
        onLongClicked = { item ->
            onDismiss()
            itemLongClicked(item)
        }
    )
}

/** Minimal java.net.URLDecoder.decode(url, "UTF-8") substitute. */
private fun urlDecode(encoded: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < encoded.length) {
        val c = encoded[i]
        when {
            c == '%' -> {
                val hex = encoded.getOrNull(i + 1)?.toString().orEmpty() +
                        encoded.getOrNull(i + 2)?.toString().orEmpty()
                val value = hex.takeIf { it.length == 2 }?.toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte())
                    i += 3
                } else {
                    bytes.add(c.code.toByte())
                    i++
                }
            }

            c == '+' -> {
                bytes.add(' '.code.toByte())
                i++
            }

            else -> {
                bytes.addAll(c.toString().encodeToByteArray().toList())
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}

@Composable
private fun ContextMenuItems(
    url: String = "",
    shouldShowAdBlock: Boolean = true,
    shouldShowTranslateImage: Boolean = false,
    showIcons: Boolean = true,
    isEbookMode: Boolean = false,
    hoveredItem: ContextMenuItemType? = null,
    onClicked: (ContextMenuItemType) -> Unit,
    onLongClicked: (ContextMenuItemType) -> Unit = {},
) {
    // hoveredItem changes on every touch-move during long-press drag; don't
    // rebuild the menu model (or re-decode the url) per hover change.
    val menuLayout = remember(isEbookMode) { createMenuLayout(isEbookMode) }
    val decodedUrl = remember(url) { urlDecode(url) }

    Column(
        modifier = Modifier
            .wrapContentHeight()
            .width(320.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .horizontalScroll(rememberScrollState()),
        ) {
            menuLayout.firstRowItems.forEach { item ->
                ContextMenuItem(
                    titleResId = item.titleResId,
                    showIcon = showIcons,
                    imageVector = item.imageVector,
                    iconResId = item.iconResId,
                    isHovered = hoveredItem == item.type,
                    onLongClicked = { onLongClicked(item.type) }
                ) {
                    onClicked(item.type)
                }
            }
        }
        HorizontalSeparator()
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center
        ) {
            menuLayout.secondRowItems.filter { item ->
                item.shouldShow(url, shouldShowAdBlock, shouldShowTranslateImage)
            }.forEach { item ->
                ContextMenuItem(
                    titleResId = item.titleResId,
                    showIcon = showIcons,
                    imageVector = item.imageVector,
                    iconResId = item.iconResId,
                    isHovered = hoveredItem == item.type,
                    onLongClicked = { onLongClicked(item.type) }
                ) {
                    onClicked(item.type)
                }
            }
        }
        HorizontalSeparator()
        Text(
            decodedUrl,
            Modifier.padding(4.dp),
            color = MaterialTheme.colors.onBackground,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

@Composable
fun ContextMenuItem(
    titleResId: StringResource,
    showIcon: Boolean = false,
    imageVector: ImageVector? = null,
    iconResId: DrawableResource? = null,
    isHovered: Boolean = false,
    onLongClicked: () -> Unit = {},
    onClicked: () -> Unit = {},
) {
    Box {
        if (isHovered) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colors.onBackground, shape = CircleShape)
                    .align(Alignment.TopCenter)
            )
        }
        MenuItem(
            titleResId = titleResId,
            iconResId = iconResId,
            imageVector = imageVector,
            isLargeType = true,
            showIcon = showIcon,
            onLongClicked = onLongClicked,
            onClicked = onClicked
        )
    }
}

enum class ContextMenuItemType {
    NewTabForeground, NewTabBackground,
    ShareLink, SelectText, OpenWith,
    SaveBookmark, SaveAs,
    SplitScreen, AdBlock, TranslateImage, Tts, Edit, Delete, Summarize, GotoLink
}

@Composable
fun PreviewContextMenuItems() {
    MyTheme {
        ContextMenuItems("abc", showIcons = false, onClicked = { })
    }
}
