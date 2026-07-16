package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.TabUnselected
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.database.Bookmark
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.main_menu_new_tab
import info.plateaukao.einkbro.resources.main_menu_new_tabOpen
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.menu_edit
import info.plateaukao.einkbro.resources.split_screen
import info.plateaukao.einkbro.view.compose.MyTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Context menu shown on a bookmark long-press. On Android this is a
 * DialogFragment anchored at the touch point; here it is a plain composable
 * hosted by whatever dialog/popup the caller opens.
 */
@Composable
fun BookmarkContextMenuScreen(
    bookmark: Bookmark = Bookmark("EinkBro", "https://github.com/plateaukao/einkbro"),
    allowEdit: Boolean = true,
    onClicked: (ContextMenuItemType) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .wrapContentHeight()
            .background(MaterialTheme.colors.background)
            .width(if (bookmark.isDirectory) 200.dp else 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = bookmark.title,
            Modifier.padding(4.dp),
            color = MaterialTheme.colors.onBackground,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        HorizontalSeparator()
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .horizontalScroll(rememberScrollState()),
        ) {
            if (!bookmark.isDirectory) {
                ContextMenuItem(Res.string.main_menu_new_tabOpen, true, Icons.Outlined.Tab) {
                    onClicked(ContextMenuItemType.NewTabForeground)
                }
                ContextMenuItem(Res.string.main_menu_new_tab, true, Icons.Outlined.TabUnselected) {
                    onClicked(ContextMenuItemType.NewTabBackground)
                }
                ContextMenuItem(Res.string.split_screen, true, Icons.Outlined.ViewStream) {
                    onClicked(ContextMenuItemType.SplitScreen)
                }
            }
            if (allowEdit) {
                ContextMenuItem(Res.string.menu_edit, true, Icons.Outlined.Edit) { onClicked(ContextMenuItemType.Edit) }
            }
            ContextMenuItem(Res.string.menu_delete, true, Icons.Outlined.Delete) {
                onClicked(ContextMenuItemType.Delete)
            }
        }
    }
}


@Composable
fun PreviewBookmarkContextMenuScreen() {
    MyTheme {
        BookmarkContextMenuScreen(
            bookmark = Bookmark("EinkBro", "https://github.com/plateaukao/einkbro"),
            allowEdit = true,
            onClicked = {},
        )
    }
}
