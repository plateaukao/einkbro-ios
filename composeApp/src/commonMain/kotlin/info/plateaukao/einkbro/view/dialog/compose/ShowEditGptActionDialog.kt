package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import info.plateaukao.einkbro.util.NoDimAlertDialog as AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.view.compose.SelectableText
import org.jetbrains.compose.resources.stringResource

/**
 * Entry composable for adding (editActionIndex = -1) or editing a GPT action.
 * Persists the modified list back into ConfigManager, like the Android dialog.
 */
@Composable
fun ShowEditGptActionDialogContent(
    editActionIndex: Int = -1,
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    var actionList = config.ai.gptActionList
    GptActionDialog(
        editActionIndex,
        if (editActionIndex >= 0) actionList[editActionIndex] else createDefaultGptAction(),
        config.ai.getGptTypeModelMap(),
        okAction = { modifiedAction ->
            actionList = actionList.toMutableList().apply {
                if (editActionIndex >= 0) set(editActionIndex, modifiedAction)
                else add(modifiedAction)
            }
            config.ai.gptActionList = actionList
            onDismiss()
        },
        dismissAction = { onDismiss() }
    )
}

private fun createDefaultGptAction(): ChatGPTActionInfo {
    return ChatGPTActionInfo(
        "",
        "",
        "",
        GptActionType.Default,
        AppServices.config.ai.getDefaultActionModel()
    )
}

// Ported (privately) from activity/GptActionsActivity.kt, which is outside this
// bundle. If that file gets ported too, this copy can be dropped in favor of
// the public one there.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GptActionDialog(
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
private fun ToggleableTextField(
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
