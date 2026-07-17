package info.plateaukao.einkbro.setting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import info.plateaukao.einkbro.util.NoDimAlertDialog as AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.util.NoDimDialog as Dialog
import androidx.navigation.NavHostController
import info.plateaukao.einkbro.BuildConfig
import info.plateaukao.einkbro.preference.EinkImageAdjustment
import info.plateaukao.einkbro.preference.EinkImageMode
import info.plateaukao.einkbro.preference.ToolbarPosition
import info.plateaukao.einkbro.preference.toggle
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.unit.ViewUnit
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.getString
import info.plateaukao.einkbro.view.dialog.DialogManager
import info.plateaukao.einkbro.view.dialog.compose.HorizontalSeparator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun SettingItemUi(
    setting: SettingItemInterface,
    isChecked: Boolean = false,
    extraTitlePostfix: String = "",
    showBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Keep a thin border for the resting (incl. toggled-on) state; only show the
    // bold border as transient press feedback. isChecked is retained for callers
    // but no longer thickens the border (see discussion #605).
    val borderWidth = if (pressed) 3.dp else 1.dp
    val height = 80.dp
    val title = setting.titleResId?.let { stringResource(it) }.orEmpty()
    var modifier = Modifier
        .fillMaxWidth()
        .testTag(title)
        .height(height)
        .clickable(
            indication = null,
            interactionSource = interactionSource,
        ) { onClick?.invoke() }
    if (showBorder) modifier =
        modifier.border(borderWidth, MaterialTheme.colors.onBackground, RoundedCornerShape(7.dp))

    Row(
        modifier = modifier.then(
            if (setting is BooleanSettingItem) Modifier.padding(
                0.dp,
                0.dp,
                55.dp,
                0.dp
            ) else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconId = setting.iconId
        if (iconId != null) {
            Icon(
                imageVector = vectorResource(iconId), contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxHeight(),
                tint = MaterialTheme.colors.onBackground
            )
        }
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
        )
        Column {
            Text(
                modifier = Modifier.wrapContentWidth(),
                text = title + extraTitlePostfix,
                fontSize = 16.sp,
                color = MaterialTheme.colors.onBackground
            )
            val summaryResId = setting.summaryResId
            if (summaryResId != null) {
                Spacer(
                    modifier = Modifier
                        .height(5.dp)
                        .fillMaxWidth()
                )
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = stringResource(summaryResId),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    }
}

@Composable
fun DividerSettingItemUi(
    title: StringResource? = null,
    supportTwoSpan: Boolean = false,
) {
    if (!supportTwoSpan) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            HorizontalSeparator()
            if (title != null) {
                Text(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(5.dp),
                    text = stringResource(title),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    } else {
        if (title != null) {
            Text(
                modifier = Modifier
                    .padding(5.dp),
                text = stringResource(title),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )
        }
    }
}

@Composable
fun BooleanSettingItemUi(
    setting: BooleanSettingItem,
    showBorder: Boolean = false,
) {
    val checked = remember(setting) { mutableStateOf(setting.config.get()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        SettingItemUi(
            setting = setting, checked.value,
            showBorder = showBorder
        ) {
            checked.value = !checked.value
            setting.config.toggle()
        }

        Switch(
            checked = checked.value,
            onCheckedChange = {
                checked.value = it
                setting.config.set(it)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colors.onBackground,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Gray,
                checkedTrackColor = MaterialTheme.colors.onBackground,
            )
        )
    }
}

@Composable
fun <T> ValueSettingItemUi(
    setting: ValueSettingItem<T>,
    dialogManager: DialogManager,
    showBorder: Boolean = false,
    showValue: Boolean = true,
) {
    // NOT rememberCoroutineScope: when the software keyboard opens, the lazy
    // settings grid recycles this row out of composition, which would cancel
    // the scope and thereby the pending getTextInput — dismissing the dialog
    // the moment its field is focused. A plain MainScope survives recycling.
    val coroutineScope = remember { kotlinx.coroutines.MainScope() }
    val currentValue = remember(setting) { mutableStateOf(setting.config.get()) }
    SettingItemUi(
        setting = setting,
        extraTitlePostfix = if (showValue) ": ${currentValue.value}" else "",
        showBorder = showBorder,
    ) {
        coroutineScope.launch {
            val value = dialogManager.getTextInput(
                setting.titleResId,
                setting.summaryResId,
                setting.config.get()
            ) ?: return@launch
            @Suppress("UNCHECKED_CAST")
            if (setting.config.get() is Int) {
                val intValue = value.toIntOrNull() ?: return@launch
                setting.config.set(intValue as T)
                currentValue.value = intValue as T
            } else {
                setting.config.set(value as T)
                currentValue.value = value as T
            }
        }
    }
}

@Composable
fun GestureActionSettingItemUi(
    setting: GestureActionSettingItem,
    navController: NavHostController,
    showBorder: Boolean = false,
) {
    val context = LocalContext.current
    val entry = info.plateaukao.einkbro.browser.BrowserActionCatalog.entryOf(setting.config.get())
    val label = context.getString(entry.labelResId)
    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": $label",
        showBorder = showBorder,
    ) {
        GesturePickerState.editingSlot = setting
        navController.navigate(info.plateaukao.einkbro.activity.SettingRoute.GesturePicker.name)
    }
}

@Composable
fun <T : Enum<T>> ListSettingItemUi(
    setting: ListSettingWithEnumItem<T>,
    dialogManager: DialogManager,
    showBorder: Boolean = false,
) {
    val context = LocalContext.current
    var currentValueString =
        remember(setting) { mutableStateOf(context.getString(setting.options[setting.config.get().ordinal])) }
    val coroutineScope = rememberCoroutineScope()
    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": ${currentValueString.value}",
        showBorder = showBorder,
    ) {
        coroutineScope.launch {
            val selectedIndex = dialogManager.getSelectedOption(
                setting.titleResId,
                setting.options,
                setting.config.get().ordinal
            ) ?: return@launch
            // javaClass.enumConstants isn't available in common code; the item
            // carries its enum constants (see ListSettingWithEnumItem factory).
            setting.config.set(setting.values[selectedIndex])
            currentValueString.value = context.getString(setting.options[selectedIndex])
        }
    }
}

@Composable
fun ToolbarPositionSettingItemUi(
    setting: ToolbarPositionSettingItem,
    showBorder: Boolean = false,
) {
    val current = remember(setting) { mutableStateOf(setting.config.get()) }
    var showDialog by remember(setting) { mutableStateOf(false) }
    val label = stringResource(toolbarPositionLabelResId(current.value))

    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": $label",
        showBorder = showBorder,
    ) {
        showDialog = true
    }

    if (showDialog) {
        ToolbarPositionDialog(
            titleResId = setting.titleResId,
            initial = current.value,
            onDismiss = { showDialog = false },
            onConfirm = { pos ->
                setting.config.set(pos)
                current.value = pos
                showDialog = false
            },
        )
    }
}

private fun toolbarPositionLabelResId(position: ToolbarPosition): StringResource = when (position) {
    ToolbarPosition.Top -> Res.string.toolbar_position_top
    ToolbarPosition.Bottom -> Res.string.toolbar_position_bottom
    ToolbarPosition.Left -> Res.string.toolbar_position_left
    ToolbarPosition.Right -> Res.string.toolbar_position_right
}

@Composable
private fun ToolbarPositionDialog(
    titleResId: StringResource,
    initial: ToolbarPosition,
    onDismiss: () -> Unit,
    onConfirm: (ToolbarPosition) -> Unit,
) {
    var pending by remember { mutableStateOf(initial) }
    AlertDialog(
        modifier = Modifier
            .padding(2.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onBackground,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(2.dp),
        onDismissRequest = onDismiss,
        backgroundColor = MaterialTheme.colors.background,
        title = {
            Text(
                text = stringResource(titleResId) + ": " +
                    stringResource(toolbarPositionLabelResId(pending)),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground,
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center,
            ) {
                ToolbarPositionDiagram(
                    selected = pending,
                    onSelect = { pending = it },
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.75f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending) }) {
                Text(
                    text = "OK",
                    color = MaterialTheme.colors.onBackground,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colors.onBackground,
                )
            }
        },
    )
}

@Composable
private fun ToolbarPositionDiagram(
    selected: ToolbarPosition,
    onSelect: (ToolbarPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colors.onBackground
    val toolbarFill = MaterialTheme.colors.onBackground.copy(alpha = 0.25f)
    val edgeFraction = 0.18f
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val edgeX = w * edgeFraction
            val edgeY = h * edgeFraction
            val thin = 1.dp.toPx()
            val bold = 3.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // Gray fill the toolbar strip on the selected edge.
            when (selected) {
                ToolbarPosition.Top ->
                    drawRect(toolbarFill, Offset.Zero, Size(w, edgeY))
                ToolbarPosition.Bottom ->
                    drawRect(toolbarFill, Offset(0f, h - edgeY), Size(w, edgeY))
                ToolbarPosition.Left ->
                    drawRect(toolbarFill, Offset.Zero, Size(edgeX, h))
                ToolbarPosition.Right ->
                    drawRect(toolbarFill, Offset(w - edgeX, 0f), Size(edgeX, h))
            }

            // Outer rectangle.
            drawRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(w, h),
                style = Stroke(width = thin),
            )

            // Inner gridlines: dashed thin by default, solid bold when selected.
            fun line(start: Offset, end: Offset, isSelected: Boolean) {
                if (isSelected) drawLine(color, start, end, bold)
                else drawLine(color, start, end, thin, pathEffect = dash)
            }
            line(Offset(0f, edgeY), Offset(w, edgeY), selected == ToolbarPosition.Top)
            line(
                Offset(0f, h - edgeY),
                Offset(w, h - edgeY),
                selected == ToolbarPosition.Bottom,
            )
            line(Offset(edgeX, 0f), Offset(edgeX, h), selected == ToolbarPosition.Left)
            line(
                Offset(w - edgeX, 0f),
                Offset(w - edgeX, h),
                selected == ToolbarPosition.Right,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(edgeFraction)
                .clickable { onSelect(ToolbarPosition.Top) },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(edgeFraction)
                .clickable { onSelect(ToolbarPosition.Bottom) },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(1f - 2 * edgeFraction)
                .fillMaxWidth(edgeFraction)
                .clickable { onSelect(ToolbarPosition.Left) },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(1f - 2 * edgeFraction)
                .fillMaxWidth(edgeFraction)
                .clickable { onSelect(ToolbarPosition.Right) },
        )
    }
}

@Composable
fun EinkImageSettingItemUi(
    setting: EinkImageSettingItem,
    showBorder: Boolean = false,
) {
    val current = remember(setting) { mutableStateOf(setting.config.get()) }
    var showDialog by remember(setting) { mutableStateOf(false) }

    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": ${stringResource(current.value.labelResId)}",
        showBorder = showBorder,
    ) {
        showDialog = true
    }

    if (showDialog) {
        EinkImageAdjustmentDialog(
            titleResId = setting.titleResId,
            initial = current.value,
            initialMode = setting.modeConfig.get(),
            onDismiss = { showDialog = false },
            onConfirm = { adjustment, mode ->
                setting.config.set(adjustment)
                setting.modeConfig.set(mode)
                current.value = adjustment
                showDialog = false
            },
        )
    }
}

/**
 * Approximates the FAST mode CSS filter (brightness/contrast/saturate) so the
 * preview shows what pages will render. Keep the factors in sync with
 * WebViewReaderHelper.einkImageFilterCss().
 */
private fun cssFilterColorMatrix(strength: Int): ColorMatrix {
    val t = strength / 100f
    val b = 1f + 0.15f * t
    val c = 1f + 0.2f * t
    val s = 1f + 0.8f * t
    val offset = 127.5f * (1f - c)
    // Android built saturation * contrast * brightness via postConcat; Compose's
    // timesAssign multiplies on the right, so apply in the same order.
    val matrix = ColorMatrix().apply { setToSaturation(s) }
    matrix *= ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    ) // contrast
    matrix *= ColorMatrix().apply { setToScale(b, b, b, 1f) } // brightness
    return matrix
}

@Composable
private fun RowScope.EinkOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .weight(1f)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colors.onBackground
                else MaterialTheme.colors.onBackground.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun EinkImageAdjustmentDialog(
    titleResId: StringResource,
    initial: EinkImageAdjustment,
    initialMode: EinkImageMode,
    onDismiss: () -> Unit,
    onConfirm: (EinkImageAdjustment, EinkImageMode) -> Unit,
) {
    var pending by remember { mutableStateOf(initial) }
    var pendingMode by remember { mutableStateOf(initialMode) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colors.background, RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colors.onBackground,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // On Android, QUALITY mode ran the native EinkImageProcessor over a
            // decoded Bitmap; in the catalog both modes are previewed with the
            // CSS color-matrix approximation applied as a ColorFilter.
            Image(
                painter = painterResource(Res.drawable.eink_image_preview),
                contentDescription = null,
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.CenterHorizontally)
                    .border(1.dp, MaterialTheme.colors.onBackground),
                contentScale = ContentScale.Fit,
                colorFilter = if (pending.strength > 0) {
                    ColorFilter.colorMatrix(cssFilterColorMatrix(pending.strength))
                } else {
                    null
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EinkImageAdjustment.entries.forEach { adjustment ->
                    EinkOptionChip(
                        text = stringResource(adjustment.labelResId),
                        selected = adjustment == pending,
                        onClick = { pending = adjustment },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EinkImageMode.entries.forEach { mode ->
                    EinkOptionChip(
                        text = stringResource(mode.labelResId),
                        selected = mode == pendingMode,
                        onClick = { pendingMode = mode },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colors.onBackground,
                    )
                }
                TextButton(onClick = { onConfirm(pending, pendingMode) }) {
                    Text(
                        text = "OK",
                        color = MaterialTheme.colors.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
fun ListSettingWithStringItemUi(
    setting: ListSettingWithStrResIdItem,
    dialogManager: DialogManager,
    showBorder: Boolean = false,
) {
    val context = LocalContext.current
    val currentIndex = setting.config.get().toInt()
    var currentValueString =
        remember(setting) { mutableStateOf(context.getString(setting.options[currentIndex])) }
    val coroutineScope = rememberCoroutineScope()
    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": ${currentValueString.value}",
        showBorder = showBorder,
    ) {
        coroutineScope.launch {
            val selectedIndex = dialogManager.getSelectedOption(
                setting.titleResId,
                setting.options,
                currentIndex
            ) ?: return@launch
            setting.config.set(selectedIndex.toString())
            currentValueString.value = context.getString(setting.options[selectedIndex])
        }
    }
}

@Composable
fun <T> ListSettingWithClassItemUi(
    setting: ListSettingWithClassItem<T>,
    dialogManager: DialogManager,
    showBorder: Boolean = false,
) {
    val configString = setting.config.get()
    var currentValueString = remember(setting) { mutableStateOf(configString) }
    val coroutineScope = rememberCoroutineScope()
    SettingItemUi(
        setting = setting,
        extraTitlePostfix = ": ${currentValueString.value}",
        showBorder = showBorder,
    ) {
        coroutineScope.launch {
            val selectedIndex = dialogManager.getSelectedOptionWithString(
                setting.titleResId,
                setting.options,
                setting.options.indexOf(configString)
            ) ?: return@launch
            val selectedValue = setting.options[selectedIndex]
            setting.config.set(selectedValue)
            currentValueString.value = selectedValue
        }
    }
}

@Composable
fun ProgressActionSettingItemUi(
    setting: ProgressActionSettingItem,
    showBorder: Boolean = false,
) {
    val progressState = remember { mutableStateOf(ProgressState()) }
    val coroutineScope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val borderWidth = if (pressed) 3.dp else 1.dp

    val title = setting.titleResId?.let { stringResource(it) }.orEmpty()
    var modifier = Modifier
        .fillMaxWidth()
        .testTag(title)
        .height(80.dp)
        .clickable(
            indication = null,
            interactionSource = interactionSource,
            enabled = !progressState.value.isRunning
        ) {
            if (!progressState.value.isRunning) {
                coroutineScope.launch {
                    progressState.value = ProgressState(isRunning = true)

                    val progressCallback = object : ProgressCallback {
                        override suspend fun updateProgress(progress: Float) {
                            progressState.value = ProgressState(
                                isRunning = true,
                                progress = progress,
                            )
                        }
                    }

                    try {
                        setting.action(progressCallback)
                    } finally {
                        progressState.value = ProgressState()
                    }
                }
            }
        }

    if (showBorder) modifier = modifier.border(borderWidth, MaterialTheme.colors.onBackground, RoundedCornerShape(7.dp))

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(if (progressState.value.isRunning) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconId = setting.iconId
            if (iconId != null) {
                Icon(
                    imageVector = vectorResource(iconId),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .fillMaxHeight(),
                    tint = MaterialTheme.colors.onBackground
                )
            }
            Spacer(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.wrapContentWidth(),
                    text = title,
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.onBackground
                )
                val summaryResId = setting.summaryResId
                if (summaryResId != null) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        text = stringResource(summaryResId),
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
        }

        if (progressState.value.isRunning && progressState.value.progress > 0f) {
            Text(
                text = "${(progressState.value.progress * 100).toInt()}%",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun SearchSettingScreen(
    query: String,
    allSettings: List<Pair<StringResource, SettingItemInterface>>,
    navController: NavHostController,
    dialogManager: DialogManager,
    linkAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The Android version also matched against English strings resolved through
    // a Locale.ENGLISH configuration context; CMP resources resolve a single
    // locale, so the search matches current-locale strings only.
    val filteredSettings = remember(query) {
        if (query.isBlank()) emptyList()
        else allSettings.filter { (_, setting) ->
            val title = setting.titleResId?.let { context.getString(it) }.orEmpty()
            val summary = setting.summaryResId?.let { context.getString(it) }.orEmpty()
            title.contains(query, ignoreCase = true) || summary.contains(query, ignoreCase = true)
        }
    }

    val columnCount = if (ViewUnit.isWideLayout(context)) 2 else 1
    val showBorder = columnCount == 2
    val supportTwoSpan = columnCount == 2

    LazyVerticalGrid(
        modifier = modifier
            .wrapContentHeight()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        columns = GridCells.Fixed(columnCount),
    ) {
        var lastCategoryResId: StringResource? = null
        var isFirstCategory = true
        filteredSettings.forEach { (categoryResId, setting) ->
            if (categoryResId != lastCategoryResId) {
                if (!isFirstCategory) {
                    item(
                        key = "divider-$categoryResId",
                        span = { GridItemSpan(if (supportTwoSpan) 2 else 1) },
                    ) {
                        DividerSettingItemUi(categoryResId, supportTwoSpan)
                    }
                }
                lastCategoryResId = categoryResId
                isFirstCategory = false
            }
            // Keyed so per-item remember state follows the setting, not the grid
            // position, as the filtered list morphs while typing.
            item(
                key = "$categoryResId-${setting.titleResId}",
                span = { GridItemSpan(if (supportTwoSpan) setting.span else 1) },
            ) {
                when (setting) {
                    is NavigateSettingItem -> SettingItemUi(setting, showBorder = showBorder) {
                        navController.navigate(setting.destination.name)
                    }

                    is ActionSettingItem -> SettingItemUi(
                        setting,
                        showBorder = showBorder
                    ) { setting.action() }

                    is GestureActionSettingItem -> GestureActionSettingItemUi(
                        setting, navController, showBorder
                    )

                    is ProgressActionSettingItem -> ProgressActionSettingItemUi(
                        setting,
                        showBorder = showBorder
                    )

                    is BooleanSettingItem -> BooleanSettingItemUi(setting, showBorder)
                    is ValueSettingItem<*> -> ValueSettingItemUi(
                        setting,
                        dialogManager,
                        showBorder,
                        setting.showValue
                    )

                    is ListSettingWithEnumItem<*> -> ListSettingItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is ToolbarPositionSettingItem -> ToolbarPositionSettingItemUi(
                        setting,
                        showBorder
                    )

                    is EinkImageSettingItem -> EinkImageSettingItemUi(
                        setting,
                        showBorder
                    )

                    is ListSettingWithStrResIdItem -> ListSettingWithStringItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is ListSettingWithClassItem<*> -> ListSettingWithClassItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is LinkSettingItem -> SettingItemUi(
                        setting,
                        showBorder = showBorder
                    ) { linkAction(setting.url) }

                    is VersionSettingItem -> {
                        val version = " v${BuildConfig.VERSION_NAME}"
                        SettingItemUi(setting, false, version, showBorder) {
                            navController.navigate(setting.destination.name)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun SettingScreen(
    navController: NavHostController,
    settings: List<SettingItemInterface>,
    dialogManager: DialogManager,
    linkAction: (String) -> Unit,
    defaultGridSize: Int = 1,
) {
    val context = LocalContext.current
    val columnCount = if (ViewUnit.isWideLayout(context) || defaultGridSize == 2) 2 else 1
    LazyVerticalGrid(
        modifier = Modifier
            .wrapContentHeight()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        columns = GridCells.Fixed(columnCount),
    ) {
        val showBorder = columnCount == 2
        val supportTwoSpan = columnCount == 2
        settings.forEach { setting ->
            item(span = { GridItemSpan(if (supportTwoSpan) setting.span else 1) }) {
                when (setting) {
                    is NavigateSettingItem -> SettingItemUi(setting, showBorder = showBorder) {
                        navController.navigate(setting.destination.name)
                    }

                    is ActionSettingItem -> SettingItemUi(
                        setting,
                        showBorder = showBorder
                    ) { setting.action() }

                    is GestureActionSettingItem -> GestureActionSettingItemUi(
                        setting, navController, showBorder
                    )

                    is ProgressActionSettingItem -> ProgressActionSettingItemUi(
                        setting,
                        showBorder = showBorder
                    )

                    is BooleanSettingItem -> BooleanSettingItemUi(setting, showBorder)
                    is ValueSettingItem<*> -> ValueSettingItemUi(
                        setting,
                        dialogManager,
                        showBorder,
                        setting.showValue
                    )

                    is DividerSettingItem ->
                        DividerSettingItemUi(setting.titleResId, supportTwoSpan)

                    is ListSettingWithEnumItem<*> -> ListSettingItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is ToolbarPositionSettingItem -> ToolbarPositionSettingItemUi(
                        setting,
                        showBorder
                    )

                    is EinkImageSettingItem -> EinkImageSettingItemUi(
                        setting,
                        showBorder
                    )

                    is ListSettingWithStrResIdItem -> ListSettingWithStringItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is ListSettingWithClassItem<*> -> ListSettingWithClassItemUi(
                        setting,
                        dialogManager,
                        showBorder
                    )

                    is LinkSettingItem -> SettingItemUi(
                        setting,
                        showBorder = showBorder
                    ) { linkAction(setting.url) }

                    is VersionSettingItem -> {
                        val version = " v${BuildConfig.VERSION_NAME}"
                        SettingItemUi(setting, false, version, showBorder) {
                            navController.navigate(setting.destination.name)
                        }
                    }
                }
            }
        }
    }
}
