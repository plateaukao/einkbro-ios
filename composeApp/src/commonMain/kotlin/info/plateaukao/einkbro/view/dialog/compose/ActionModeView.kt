package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.util.screenWidthDp
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.data.MenuInfo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Text-selection action-mode menu. On Android tapping an item could forward
 * an Intent to the host; the shim MenuInfo has no Intent, so closing items
 * just invoke [onClicked].
 */
@Composable
fun ActionModeMenu(
    menus: MutableState<List<MenuInfo>>,
    showIcons: Boolean = true,
    onClicked: () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .background(MaterialTheme.colors.background)
            .wrapContentHeight()
            .width(280.dp)
            .border(1.dp, MaterialTheme.colors.onBackground, RoundedCornerShape(7.dp))
    ) {
        val menuInfos = menus.value
        items(menuInfos.size) { index ->
            val info = menuInfos[index]
            ActionMenuItem(
                info.title,
                if (showIcons) info.drawable else null,
                if (showIcons) info.imageVector else null,
                cornerDrawable = if (showIcons) info.cornerDrawable else null,
                onClicked = {
                    info.action?.invoke()
                    if (info.closeMenu) onClicked()
                },
                onLongClicked = {
                    info.longClickAction?.invoke()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionMenuItem(
    title: String,
    iconDrawable: DrawableResource?,
    imageVector: ImageVector? = null,
    cornerDrawable: DrawableResource? = null,
    onClicked: () -> Unit = {},
    onLongClicked: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val screenWidth = screenWidthDp()
    val width = when {
        screenWidth > 500 -> 55.dp
        else -> 45.dp
    }

    val fontSize = if (cornerDrawable != null || (iconDrawable == null && imageVector == null)) 12.sp else
        if (screenWidth > 500) 10.sp else 8.sp
    Box(
        modifier = Modifier
            .width(width)
            .wrapContentHeight()
    ) {
        if (pressed) {
            Box(
                modifier = Modifier
                    .padding(start = (width + 16.dp) / 2, top = 4.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colors.onBackground, shape = CircleShape)
                    .align(Alignment.TopStart)
            )
        }
        if (cornerDrawable != null) {
            // GPT action: title centered, small type icon at bottom-right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .combinedClickable(
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onClicked,
                        onLongClick = onLongClicked,
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = fontSize,
                    fontSize = fontSize,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(bottom = 14.dp, top = 14.dp),
                )
                Image(
                    painter = painterResource(cornerDrawable),
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(8.dp)
                    .combinedClickable(
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onClicked,
                        onLongClick = onLongClicked,
                    ),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (iconDrawable != null) {
                    Image(
                        painter = painterResource(iconDrawable),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(horizontal = 6.dp),
                    )
                }
                if (imageVector != null) {
                    Image(
                        imageVector = imageVector,
                        colorFilter = ColorFilter.tint(MaterialTheme.colors.onBackground),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(horizontal = 6.dp),
                    )
                }
                if (title.isNotEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        text = title,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = fontSize,
                        fontSize = fontSize,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
        }
    }
}

/** Catalog preview: the whole action-mode grid with representative entries. */
@Composable
fun PreviewActionModeMenu() {
    MyTheme {
        val menus = remember {
            mutableStateOf(
                listOf(
                    MenuInfo("Copy", imageVector = Icons.Outlined.ContentCopy),
                    MenuInfo("Search", imageVector = Icons.Outlined.Search),
                    MenuInfo("Translate", imageVector = Icons.Outlined.Translate),
                    MenuInfo("Share", imageVector = Icons.Outlined.Share),
                    MenuInfo("Summarize"),
                )
            )
        }
        ActionModeMenu(menus = menus, showIcons = true, onClicked = {})
    }
}

@Composable
fun PreviewActionMenuItem() {
    MyTheme {
        ActionMenuItem(
            title = "Title",
            iconDrawable = null,
            imageVector = null,
            onClicked = {},
            onLongClicked = {},
        )
    }
}
