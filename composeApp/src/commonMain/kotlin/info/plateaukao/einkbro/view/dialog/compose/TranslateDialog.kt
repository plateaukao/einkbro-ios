package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.GptActionScope
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.unit.ShareUtil
import info.plateaukao.einkbro.unit.ViewUnit
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.screenHeightDp
import info.plateaukao.einkbro.util.screenWidthDp
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.viewmodel.TRANSLATE_API
import info.plateaukao.einkbro.viewmodel.TranslationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource

/**
 * Entry composable for the translation popup; was TranslateDialogFragment.Content().
 * The stub view model fakes a translation on open so the popup renders with a
 * sample result; the language picker is a stub that resolves to cancelled.
 */
@Composable
fun TranslateDialogContent(
    translationViewModel: TranslationViewModel = remember { TranslationViewModel() },
    isWholePageMode: Boolean = false,
    closeAction: () -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Task mode streams its own content; auto-translating here would race
        // the task stream and show an unrelated translation first.
        if (!translationViewModel.isTaskStreamActive) translationViewModel.translate()
    }

    TranslateResponse(
        translationViewModel,
        showExtraIcons = true,
        onTargetLanguageClick = {
            // Android opens TranslationLanguageDialog and re-translates.
            EBToast.show(context, "would show translation language picker")
        },
        closeClick = closeAction,
        isWholePageMode = isWholePageMode,
    )
}

@Composable
private fun TranslateResponse(
    viewModel: TranslationViewModel,
    showExtraIcons: Boolean,
    onTargetLanguageClick: () -> Unit,
    closeClick: () -> Unit,
    isWholePageMode: Boolean = false,
) {
    val iconSize = 40.dp
    val iconPadding = 5.dp
    val requestMessage by viewModel.inputMessage.collectAsState()
    val responseMessage by viewModel.responseMessage.collectAsState()
    val rotateScreen by viewModel.rotateResultScreen.collectAsState()
    val showRequest = remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    var viewportHeight by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.scrollSignal.collect { isUp ->
            val scrollAmount = if (isUp) -viewportHeight else viewportHeight
            if (scrollAmount != 0) {
                scrollState.scrollTo(
                    (scrollState.value + scrollAmount).coerceIn(0, scrollState.maxValue)
                )
            }
        }
    }

    val translateGoogle = remember { { viewModel.translate(TRANSLATE_API.GOOGLE) } }
    val translateNaver = remember { { viewModel.translate(TRANSLATE_API.NAVER) } }

    val maxHeight = (screenHeightDp() * 0.8).dp
    // Wide result card: tablets get a fixed 600dp; phones stretch to the dialog
    // frame's edge margins (the host turns off usePlatformDefaultWidth).
    val isTablet = ViewUnit.isTablet(LocalContext.current)

    Column(
        modifier = Modifier
            .padding(top = 6.dp, start = 6.dp, end = 6.dp)
            .run {
                if (rotateScreen) {
                    width(400.dp)
                        .height(400.dp)
                        .rotate(-90f)
                } else {
                    (if (isTablet) width(600.dp) else fillMaxWidth())
                        .heightIn(max = maxHeight)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .wrapContentHeight()
                .wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CopyButton(iconSize, iconPadding, responseMessage)

            if (isWholePageMode) {
                SaveButton(iconSize, iconPadding, viewModel)
            } else {
                if (ViewUnit.isTablet(LocalContext.current)) {
                    GptRow(viewModel)
                }
                GoogleButton(iconSize, iconPadding, translateGoogle, onTargetLanguageClick)
                if (showExtraIcons) {
                    NaverButton(iconSize, iconPadding, translateNaver)
                }
                InfoButton(showRequest, iconSize)
            }
            CloseButton(iconSize, iconPadding, closeClick)
        }
        if (!isWholePageMode && !ViewUnit.isTablet(LocalContext.current)) {
            GptRow(
                viewModel,
                modifier = Modifier.align(Alignment.End),
            )
        }
        Column(
            modifier = Modifier
                .defaultMinSize(minWidth = 300.dp)
                .wrapContentHeight()
                .width(IntrinsicSize.Max)
                .weight(1f, fill = false)
                .align(Alignment.Start)
                .onGloballyPositioned { coordinates ->
                    viewportHeight = coordinates.size.height
                }
                .conditionalScroll(
                    !viewModel.isWebViewStyle(),
                    scrollState
                ),
            horizontalAlignment = Alignment.End
        ) {
            if (showRequest.value) {
                Text(
                    text = requestMessage,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Divider()
            }
            if (viewModel.isWebViewStyle() && responseMessage.text != "...") {
                WebResultView(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    responseMessage.text
                )
            } else {
                Text(
                    text = responseMessage,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
        RoundedDragBar()
    }
}

@Composable
private fun CloseButton(
    iconSize: Dp,
    iconPadding: Dp,
    closeClick: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close Icon",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(iconPadding)
            .clickable { closeClick() }
    )
}

@Composable
private fun InfoButton(
    showRequest: MutableState<Boolean>,
    iconSize: Dp,
) {
    Icon(
        imageVector = if (showRequest.value) Icons.Default.KeyboardArrowUp else Icons.Outlined.Info,
        contentDescription = "Info Icon",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(10.dp)
            .clickable {
                showRequest.value = !showRequest.value
            }
    )
}

@Composable
private fun NaverButton(
    iconSize: Dp,
    iconPadding: Dp,
    translateNaver: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "Naver dict icon",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(iconPadding)
            .clickable {
                translateNaver()
            }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GoogleButton(
    iconSize: Dp,
    iconPadding: Dp,
    translateGoogle: () -> Unit,
    onTargetLanguageClick: () -> Unit,
) {
    Icon(
        imageVector = vectorResource(Res.drawable.ic_translate_google),
        contentDescription = "Google Translate",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(iconPadding)
            .combinedClickable(
                onClick = translateGoogle,
                onLongClick = onTargetLanguageClick
            )
    )
}

@Composable
private fun SaveButton(
    iconSize: Dp,
    iconPadding: Dp,
    viewModel: TranslationViewModel,
) {
    val coroutineScope = rememberCoroutineScope()
    val saveIcon = Icons.Default.Save
    var currentIcon by remember { mutableStateOf(saveIcon) }
    Icon(
        imageVector = currentIcon,
        contentDescription = "Save",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(iconPadding)
            .clickable {
                coroutineScope.launch {
                    viewModel.saveTranslationResult()
                    currentIcon = Icons.Filled.Done
                    delay(1000)
                    currentIcon = saveIcon
                }
            }
    )
}

@Composable
private fun CopyButton(
    iconSize: Dp,
    iconPadding: Dp,
    responseMessage: AnnotatedString,
) {
    val context = LocalContext.current
    Icon(
        imageVector = Icons.Default.ContentCopy,
        contentDescription = "Copy text",
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier
            .size(iconSize)
            .padding(iconPadding)
            .clickable { ShareUtil.copyToClipboard(context, responseMessage.text) }
    )
}

@Composable
private fun GptRow(
    translationViewModel: TranslationViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        val coroutineScope = rememberCoroutineScope()

        val saveIcon = Icons.Default.Save
        var currentIcon by remember { mutableStateOf(saveIcon) }


        ActionMenuItem(
            "",
            imageVector = currentIcon,
            onClicked = {
                coroutineScope.launch {
                    translationViewModel.saveTranslationResult()
                    currentIcon = Icons.Filled.Done
                    delay(1000) // Wait for 0.5 seconds
                    currentIcon = saveIcon
                }
            }
        )
        translationViewModel.getGptActionList()
            .mapIndexed { index, gptActionInfo -> index to gptActionInfo }
            .filter { it.second.scope == GptActionScope.TextSelection }
            .forEach { (index, gptActionInfo) ->
                val gptClicked = remember {
                    {
                        translationViewModel.gptActionInfo = gptActionInfo
                        translationViewModel.translate(TRANSLATE_API.LLM)
                    }
                }
                val gptLongClicked =
                    remember { { translationViewModel.showEditGptActionDialog(index) } }
                ActionMenuItem(
                    gptActionInfo.name,
                    onClicked = gptClicked,
                    onLongClicked = gptLongClicked
                )
            }
    }
}

/**
 * Local, simplified port of ActionMenuItem from ActionModeView.kt (owned by
 * another bundle; the Android original also takes Drawable icons).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionMenuItem(
    title: String,
    imageVector: ImageVector? = null,
    onClicked: () -> Unit = {},
    onLongClicked: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val width = if (screenWidthDp() > 500) 55.dp else 45.dp
    val fontSize = if (imageVector == null) 12.sp
    else if (screenWidthDp() > 500) 10.sp else 8.sp

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .combinedClickable(
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = onClicked,
                    onLongClick = onLongClicked,
                )
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = title,
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    fontSize = fontSize,
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }
    }
}

@Composable
fun RoundedDragBar(width: Dp = 100.dp) {
    Box(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(4.dp)
                .align(Alignment.Center)
                .background(
                    color = Color.Gray,
                    shape = RoundedCornerShape(50) // Use a high value to ensure fully rounded corners
                )
        )
    }
}

/**
 * The Android original embeds the app's WebView (AndroidView) to render the
 * Naver dict result page; the port shows the result URL as text instead.
 */
@Composable
private fun WebResultView(modifier: Modifier, webContent: String) {
    val context = LocalContext.current
    Text(
        text = webContent,
        color = MaterialTheme.colors.onBackground,
        modifier = modifier
            .heightIn(max = 400.dp)
            .width(500.dp)
            .padding(10.dp)
            .clickable { EBToast.show(context, "would load in WebView: $webContent") },
    )
}

@Composable
fun PreviewRoundedDragBar() {
    RoundedDragBar()
}

private fun Modifier.conditionalScroll(applyScroll: Boolean, scrollState: ScrollState): Modifier =
    this.then(
        if (applyScroll) Modifier.verticalScroll(scrollState) else Modifier
    )
