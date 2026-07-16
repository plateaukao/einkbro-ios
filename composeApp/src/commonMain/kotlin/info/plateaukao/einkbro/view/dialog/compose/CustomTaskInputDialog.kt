package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

@Composable
fun CustomTaskInputDialogContent(
    onSubmit: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
        var prompt by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .width(320.dp)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.task_custom),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.task_custom_hint),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                maxLines = 4,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    cursorColor = MaterialTheme.colors.onBackground,
                    focusedBorderColor = MaterialTheme.colors.onBackground,
                    unfocusedBorderColor = MaterialTheme.colors.onBackground,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDismiss() }) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colors.onBackground,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    if (prompt.isNotBlank()) {
                        onSubmit(prompt.trim())
                        onDismiss()
                    }
                }) {
                    Text(
                        text = stringResource(Res.string.task_run),
                        color = MaterialTheme.colors.onBackground,
                    )
                }
            }
        }
    }
