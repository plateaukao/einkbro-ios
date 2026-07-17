package info.plateaukao.einkbro.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import info.plateaukao.einkbro.util.NoDimAlertDialog as AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionDisplay
import info.plateaukao.einkbro.preference.GptActionScope
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.empty_whitelist_hint
import info.plateaukao.einkbro.resources.gpt_action_drag_handle
import info.plateaukao.einkbro.resources.gpt_actions_title
import info.plateaukao.einkbro.resources.gpt_scope
import info.plateaukao.einkbro.resources.gpt_scope_text_selection
import info.plateaukao.einkbro.resources.gpt_scope_whole_page
import info.plateaukao.einkbro.resources.ic_chat_gpt
import info.plateaukao.einkbro.resources.ic_gemini
import info.plateaukao.einkbro.resources.ic_ollama
import info.plateaukao.einkbro.resources.list_empty
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.view.compose.EmptyListPlaceholder
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.MyTheme
import info.plateaukao.einkbro.view.compose.SelectableText
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Port of GptActionsActivity: the GPT custom-action list with reorder support
 * plus the action editor dialog. Backed by AppServices.config.ai; seeded with
 * sample actions when the list is empty so the catalog has data to show.
 */
@Composable
fun GptActionsScreen(
    actionIndex: Int = -1,
    onClose: () -> Unit = {},
) {
    val config = AppServices.config
    val defaultActionType = config.ai.getDefaultActionType()

    val actionList = remember {
        val existing = config.ai.gptActionList
        val seeded = if (existing.size <= 1) {
            sampleGptActions.also { config.ai.gptActionList = it }
        } else existing
        mutableStateOf(seeded)
    }
    var showDialog by remember { mutableStateOf(false) }
    var editActionIndex by remember { mutableIntStateOf(actionIndex) }

    ListScaffold(
        title = stringResource(Res.string.gpt_actions_title),
        onBack = onClose,
        actions = {
            IconButton(onClick = {
                actionList.value = emptyList()
                config.ai.deleteAllGptActions()
            }) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.menu_delete)
                )
            }
            IconButton(onClick = {
                editActionIndex = -1
                showDialog = true
            }) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.whitelist_add)
                )
            }
        },
    ) { innerPadding ->
        GptActionListContent(
            modifier = Modifier.padding(innerPadding),
            list = actionList,
            defaultActionType = defaultActionType,
            editAction = { index ->
                editActionIndex = index
                showDialog = true
            },
            deleteAction = { action ->
                actionList.value = actionList.value.toMutableList().apply { remove(action) }
                config.ai.deleteGptAction(action)
            },
            reorderAction = { from, to ->
                val newList = actionList.value.toMutableList().apply {
                    add(to, removeAt(from))
                }
                actionList.value = newList
                config.ai.gptActionList = newList
            }
        )
    }
    if (showDialog) {
        GptActionDialog(
            editActionIndex,
            if (editActionIndex >= 0)
                actionList.value[editActionIndex] else createDefaultGptAction(),

            config.ai.getGptTypeModelMap(),
            okAction = { modifiedAction ->
                actionList.value = actionList.value.toMutableList().apply {
                    if (editActionIndex >= 0) set(editActionIndex, modifiedAction)
                    else add(modifiedAction)
                }
                config.ai.gptActionList = actionList.value
                showDialog = false
            },
            dismissAction = { showDialog = false }
        )
    }
}

private fun createDefaultGptAction(): ChatGPTActionInfo {
    return ChatGPTActionInfo(
        "New Action",
        "",
        "",
        GptActionType.Default,
        AppServices.config.ai.getDefaultActionModel()
    )
}

private val sampleGptActions = listOf(
    ChatGPTActionInfo(
        name = "Translate to English",
        systemMessage = "You are a good interpreter.",
        userMessage = "Translate following content to English:",
        actionType = GptActionType.OpenAi,
        model = "gpt-4o",
    ),
    ChatGPTActionInfo(
        name = "Summarize page",
        systemMessage = "You are a concise summarizer.",
        userMessage = "Summarize in 50 words:",
        actionType = GptActionType.Gemini,
        model = "gemini-2.5-flash",
        display = GptActionDisplay.NewTab,
        scope = GptActionScope.WholePage,
    ),
    ChatGPTActionInfo(
        name = "Explain like I'm five",
        systemMessage = "",
        userMessage = "Explain this simply:",
        actionType = GptActionType.SelfHosted,
        model = "llama3",
        display = GptActionDisplay.SplitScreen,
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GptActionListContent(
    modifier: Modifier = Modifier,
    list: MutableState<List<ChatGPTActionInfo>>,
    defaultActionType: GptActionType,
    editAction: (Int) -> Unit, // edit action with index
    deleteAction: (ChatGPTActionInfo) -> Unit = {},
    reorderAction: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    if (list.value.isEmpty()) {
        EmptyListPlaceholder(
            stringResource(Res.string.list_empty) + stringResource(Res.string.empty_whitelist_hint)
        )
    } else {
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            reorderAction(from.index, to.index)
        }

        LazyColumn(
            modifier = modifier.padding(10.dp),
            state = lazyListState,
        ) {
            itemsIndexed(list.value, key = { _, action -> action.id }) { index, gptAction ->
                val actionType = gptAction.actionType.takeIf { it != GptActionType.Default }
                    ?: defaultActionType

                val iconRes = when (actionType) {
                    GptActionType.OpenAi -> Res.drawable.ic_chat_gpt
                    GptActionType.SelfHosted -> Res.drawable.ic_ollama
                    GptActionType.Gemini -> Res.drawable.ic_gemini
                    else -> Res.drawable.ic_chat_gpt
                }

                ReorderableItem(reorderableState, key = gptAction.id) { _ ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clickable {
                                editAction(index)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // icon: action type
                        Icon(
                            modifier = Modifier.wrapContentWidth(),
                            imageVector = vectorResource(iconRes),
                            contentDescription = "Action Type",
                        )
                        Spacer(modifier = Modifier.width(15.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp),
                                text = gptAction.name,
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onBackground
                            )
                            if (gptAction.scope == GptActionScope.WholePage) {
                                Text(
                                    modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp),
                                    text = stringResource(Res.string.gpt_scope_whole_page),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onBackground
                                )
                            }
                            if (gptAction.model.isNotEmpty()) {
                                Text(
                                    modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp),
                                    text = gptAction.model,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onBackground
                                )
                            }
                        }
                        Icon(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .draggableHandle(),
                            imageVector = Icons.Outlined.DragHandle,
                            contentDescription = stringResource(Res.string.gpt_action_drag_handle),
                            tint = MaterialTheme.colors.onBackground
                        )
                        IconButton(onClick = {
                            deleteAction(gptAction)
                        }) {
                            Icon(
                                tint = MaterialTheme.colors.onBackground,
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.menu_delete)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GptActionDialog(
    editActionIndex: Int,
    action: ChatGPTActionInfo,
    gptTypeModelMap: Map<GptActionType, String>,
    okAction: (ChatGPTActionInfo) -> Unit,
    dismissAction: () -> Unit,
) {
    // Keyed initialization: the previous unconditional assignment block ran on
    // every recomposition of this scope and silently wiped user-typed values.
    val isEdit = editActionIndex >= 0
    val name = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.name else "")
    }
    val systemPrompt = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.systemMessage else "")
    }
    val userPrompt = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.userMessage else "")
    }
    val currentActionType = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.actionType else GptActionType.Default)
    }
    val currentActionDisplay = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.display else GptActionDisplay.Popup)
    }
    val currentActionScope = remember(editActionIndex, action) {
        mutableStateOf(if (isEdit) action.scope else GptActionScope.TextSelection)
    }
    val model = remember(editActionIndex, action) { mutableStateOf(action.model) }

    AlertDialog(
        modifier = Modifier
            .padding(2.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onBackground,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(2.dp),
        // use caption style
        title = { Text("Action Setting", style = MaterialTheme.typography.h6) },
        text = {
            Column {
                TextField(
                    modifier = Modifier.padding(2.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                    ),
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Name") }
                )
                ToggleableTextField(
                    modifier = Modifier.padding(2.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                    ),
                    value = systemPrompt.value,
                    onValueChange = { systemPrompt.value = it },
                    label = { Text("System Prompt") }
                )
                ToggleableTextField(
                    modifier = Modifier.padding(2.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                    ),
                    minLines = 3,
                    value = userPrompt.value,
                    onValueChange = { userPrompt.value = it },
                    label = { Text("User Prompt") }
                )
                Text(
                    modifier = Modifier.padding(5.dp),
                    text = "Service",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground
                )
                FlowRow {
                    GptActionType.entries.map { gptActionType ->
                        val isSelect = currentActionType.value == gptActionType
                        SelectableText(
                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp),
                            selected = isSelect,
                            text = "$gptActionType",
                        ) {
                            currentActionType.value = gptActionType
                            model.value = gptTypeModelMap[gptActionType] ?: ""
                        }
                    }
                }
                TextField(
                    modifier = Modifier.padding(2.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                    ),
                    value = model.value,
                    onValueChange = { model.value = it },
                    label = { Text("model") }
                )
                FlowRow {
                    GptActionDisplay.entries.map { gptActionDisplay ->
                        val isSelect = currentActionDisplay.value == gptActionDisplay
                        SelectableText(
                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp),
                            selected = isSelect,
                            text = "$gptActionDisplay",
                        ) {
                            currentActionDisplay.value = gptActionDisplay
                        }
                    }
                }
                Text(
                    modifier = Modifier.padding(5.dp),
                    text = stringResource(Res.string.gpt_scope),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground
                )
                FlowRow {
                    GptActionScope.entries.map { gptActionScope ->
                        val isSelect = currentActionScope.value == gptActionScope
                        val scopeLabel = if (gptActionScope == GptActionScope.WholePage)
                            stringResource(Res.string.gpt_scope_whole_page)
                        else stringResource(Res.string.gpt_scope_text_selection)
                        SelectableText(
                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 3.dp),
                            selected = isSelect,
                            text = scopeLabel,
                        ) {
                            currentActionScope.value = gptActionScope
                        }
                    }
                }
            }
        },
        onDismissRequest = { dismissAction() },
        confirmButton = {
            TextButton(
                onClick = {
                    okAction(
                        ChatGPTActionInfo(
                            name.value,
                            systemPrompt.value,
                            userPrompt.value,
                            currentActionType.value,
                            model.value,
                            currentActionDisplay.value,
                            currentActionScope.value,
                        )
                    )
                }
            ) {
                Text(
                    "OK",
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
    )
}

@Composable
fun ToggleableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    label: @Composable (() -> Unit)? = null,
    placeholder: String = "",
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp)
        ) {
            label?.invoke()
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder) },
                minLines = minLines,
                colors = colors,
            )
        } else if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(vertical = 4.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun GptActionListContentPreview() {
    val actionList = remember {
        mutableStateOf(
            listOf(
                ChatGPTActionInfo(
                    "ChatGPT",
                    "system message",
                    "user message",
                    GptActionType.SelfHosted,
                    "gpt-3"
                )
            )
        )
    }
    MyTheme {
        GptActionListContent(
            list = actionList,
            defaultActionType = GptActionType.OpenAi,
            editAction = {},
            deleteAction = {}
        )
    }
}
