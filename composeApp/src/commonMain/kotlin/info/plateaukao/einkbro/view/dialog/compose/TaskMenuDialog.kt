package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.task.TaskCatalog
import info.plateaukao.einkbro.task.TaskDescriptor

@Composable
fun TaskMenuDialogContent(
    descriptors: List<TaskDescriptor> = TaskCatalog.builtIns,
    onTemplateClicked: (TaskDescriptor) -> Unit = {},
    onCustomClicked: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
        // No verticalScroll here: DialogFrame already wraps content in a
        // scrollable Box, and nesting two vertical scrolls measures this one
        // with infinite height and crashes.
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.task_menu_title),
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            descriptors.forEach { descriptor ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onTemplateClicked(descriptor)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(descriptor.displayNameResId),
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialTheme.colors.onBackground,
                    )
                    Text(
                        text = stringResource(descriptor.descriptionResId),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground,
                    )
                }
            }
            Divider(color = MaterialTheme.colors.onBackground)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onCustomClicked()
                        onDismiss()
                    }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(Res.string.task_custom),
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground,
                )
                Text(
                    text = stringResource(Res.string.task_custom_desc),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }
}
