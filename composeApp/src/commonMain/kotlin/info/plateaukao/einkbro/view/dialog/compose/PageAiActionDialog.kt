package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionType
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import org.jetbrains.compose.resources.DrawableResource

private val sampleActions = listOf(
    ChatGPTActionInfo(name = "Summarize", userMessage = "Summarize this page"),
    ChatGPTActionInfo(name = "Translate", actionType = GptActionType.Gemini),
    ChatGPTActionInfo(name = "Local model", actionType = GptActionType.SelfHosted),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageAiActionDialogContent(
    actions: List<ChatGPTActionInfo> = sampleActions,
    onActionClicked: (ChatGPTActionInfo) -> Unit = {},
    onActionLongClicked: ((ChatGPTActionInfo) -> Unit)? = null,
    onChatWithWebClicked: (() -> Unit)? = {},
    onChatWithWebLongClicked: (() -> Unit)? = null,
    onTaskRunnerClicked: (() -> Unit)? = {},
    onDismiss: () -> Unit = {},
) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.page_ai_action_title),
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.onBackground
                )
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(Res.string.settings),
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier.clickable {
                        EBToast.show(context, "would open GPT actions settings")
                        onDismiss()
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            onChatWithWebClicked?.let { handler ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                handler()
                                onDismiss()
                            },
                            onLongClick = {
                                onChatWithWebLongClicked?.invoke()
                                onDismiss()
                            }
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground
                    )
                    Text(
                        text = stringResource(Res.string.chat_with_web),
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
            onTaskRunnerClicked?.let { handler ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            handler()
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground
                    )
                    Text(
                        text = stringResource(Res.string.task_menu_title),
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                onActionClicked(action)
                                onDismiss()
                            },
                            onLongClick = {
                                onActionLongClicked?.invoke(action)
                                onDismiss()
                            }
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(actionIconRes(action)),
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground
                    )
                    Text(
                        text = action.name,
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
        }
}

private fun actionIconRes(action: ChatGPTActionInfo): DrawableResource {
    val actionType = action.actionType.takeIf { it != GptActionType.Default }
        ?: GptActionType.OpenAi
    return when (actionType) {
        GptActionType.OpenAi -> Res.drawable.ic_chat_gpt
        GptActionType.SelfHosted -> Res.drawable.ic_ollama
        GptActionType.Gemini -> Res.drawable.ic_gemini
        else -> Res.drawable.ic_chat_gpt
    }
}
