package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.menu_hide_hint_reorder
import info.plateaukao.einkbro.resources.menu_hide_hint_tap
import info.plateaukao.einkbro.resources.menu_reorder
import info.plateaukao.einkbro.resources.setting_title_hide_menu_items
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.dialog.compose.LocalMenuActions
import info.plateaukao.einkbro.view.dialog.compose.LocalMenuHideConfig
import info.plateaukao.einkbro.view.dialog.compose.MENU_GRID_COLUMNS
import info.plateaukao.einkbro.view.dialog.compose.MenuActions
import info.plateaukao.einkbro.view.dialog.compose.MenuEntry
import info.plateaukao.einkbro.view.dialog.compose.MenuHideConfig
import info.plateaukao.einkbro.view.dialog.compose.MenuItemForType
import info.plateaukao.einkbro.view.dialog.compose.MenuItemType
import info.plateaukao.einkbro.view.dialog.compose.effectiveMenuEntries
import info.plateaukao.einkbro.view.dialog.compose.encodeMenuEntries
import info.plateaukao.einkbro.view.dialog.compose.menuDisplayEntries
import info.plateaukao.einkbro.view.dialog.compose.menuDisplayToUnderlying
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * Port of Android's MenuItemHideActivity: tap items to hide/show them in the
 * main menu, or switch to reorder mode and drag them (across section
 * boundaries too). Persists ui.hiddenMenuItems / ui.menuItemOrder, which
 * MenuDialog already honors.
 */
@Composable
fun MenuItemHideScreen(onClose: () -> Unit = {}) {
    val config = AppServices.config
    var hidden by remember {
        mutableStateOf(
            config.ui.hiddenMenuItems.mapNotNull { name ->
                runCatching { MenuItemType.valueOf(name) }.getOrNull()
            }.toSet()
        )
    }
    // The *display* list is the session's source of truth so drags don't
    // micro-jitter from re-normalisation; each change persists the underlying
    // order.
    var display by remember {
        mutableStateOf(
            menuDisplayEntries(effectiveMenuEntries(config.ui.menuItemOrder))
        )
    }
    var reorderMode by remember { mutableStateOf(false) }

    ListScaffold(
        title = stringResource(Res.string.setting_title_hide_menu_items),
        onBack = onClose,
        actions = {
            IconButton(onClick = { reorderMode = !reorderMode }) {
                Icon(
                    imageVector = if (reorderMode) Icons.Filled.Check else Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(Res.string.menu_reorder),
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(
                    if (reorderMode) Res.string.menu_hide_hint_reorder
                    else Res.string.menu_hide_hint_tap
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colors.onBackground,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            CompositionLocalProvider(
                LocalMenuHideConfig provides MenuHideConfig(
                    hideMode = !reorderMode,
                    reorderMode = reorderMode,
                    hiddenItems = hidden,
                    onToggleHide = { type ->
                        hidden = if (type in hidden) hidden - type else hidden + type
                        config.ui.hiddenMenuItems = hidden.map { it.name }.toSet()
                    },
                ),
                LocalMenuActions provides MenuActions(),
            ) {
                ReorderableMenuGrid(
                    display = display,
                    reorderMode = reorderMode,
                    onReorder = { newDisplay ->
                        display = newDisplay
                        config.ui.menuItemOrder = encodeMenuEntries(
                            menuDisplayToUnderlying(newDisplay)
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableMenuGrid(
    display: List<MenuEntry>,
    reorderMode: Boolean,
    onReorder: (List<MenuEntry>) -> Unit,
) {
    val lazyGridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        val fromEntry = display.getOrNull(from.index) ?: return@rememberReorderableLazyGridState
        if (fromEntry !is MenuEntry.Item) return@rememberReorderableLazyGridState
        val newList = display.toMutableList().apply { add(to.index, removeAt(from.index)) }
        onReorder(newList)
    }
    LazyVerticalGrid(
        state = lazyGridState,
        columns = GridCells.Fixed(MENU_GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
    ) {
        display.forEachIndexed { index, entry ->
            when (entry) {
                is MenuEntry.Item -> item(key = "item_${entry.type.name}") {
                    ReorderableItem(reorderableState, key = "item_${entry.type.name}") { _ ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .then(
                                    if (reorderMode) Modifier.longPressDraggableHandle()
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            MenuItemForType(type = entry.type)
                        }
                    }
                }

                is MenuEntry.Boundary -> item(
                    key = "boundary_${entry.sectionStart.name}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    ReorderableItem(reorderableState, key = "boundary_${entry.sectionStart.name}") { _ ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            Divider(color = MaterialTheme.colors.primary, thickness = 1.dp)
                            entry.sectionStart.headerRes?.let { res ->
                                Text(
                                    text = stringResource(res),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colors.onBackground,
                                )
                            }
                        }
                    }
                }

                // Display-only empty cell. Must be a ReorderableItem so drags can cross it.
                is MenuEntry.Spacer -> item(key = "spacer_$index") {
                    ReorderableItem(reorderableState, key = "spacer_$index") { _ ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .fillMaxWidth(),
                        ) {}
                    }
                }
            }
        }
    }
}
