package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import info.plateaukao.einkbro.data.remote.ChatRole
import info.plateaukao.einkbro.viewmodel.ChatWithWebViewModel

/**
 * Native "chat with web" UI (parity Phase K). The page's raw text seeds the
 * conversation as context; each turn streams a reply from the configured GPT
 * engine into the last assistant bubble.
 */
@Composable
fun ChatWithWebDialog(
    viewModel: ChatWithWebViewModel,
    onDismiss: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val inProgress by viewModel.inProgress.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, inProgress) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .background(MaterialTheme.colors.surface)
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Chat with web",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.onSurface,
                    )
                    if (viewModel.webTitle.isNotBlank()) {
                        Text(
                            viewModel.webTitle,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            Divider(modifier = Modifier.padding(vertical = 6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message -> MessageBubble(message) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about this page…") },
                    singleLine = false,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                if (inProgress) {
                    TextButton(onClick = { viewModel.cancel() }) { Text("Stop") }
                } else {
                    TextButton(
                        enabled = input.isNotBlank(),
                        onClick = {
                            viewModel.send(input)
                            input = ""
                        },
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatWithWebViewModel.ChatUiMessage) {
    val isUser = message.role == ChatRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colors.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = if (isUser) androidx.compose.ui.text.AnnotatedString(message.text) else message.rendered,
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
