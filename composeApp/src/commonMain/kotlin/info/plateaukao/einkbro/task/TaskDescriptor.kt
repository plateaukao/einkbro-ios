package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.task_read_article_list
import info.plateaukao.einkbro.resources.task_read_article_list_desc
import org.jetbrains.compose.resources.StringResource

/**
 * Stub of the Android task registry: only the descriptor metadata that the
 * task picker dialog renders. The Android `factory: () -> BrowserTask` is
 * service-heavy (drives the WebView agent loop) and is replaced by a no-op.
 */
data class TaskDescriptor(
    val id: String,
    val displayNameResId: StringResource,
    val descriptionResId: StringResource,
    val factory: () -> Unit = {},
)

object TaskCatalog {
    val builtIns: List<TaskDescriptor> = listOf(
        TaskDescriptor(
            id = "read_article_list",
            displayNameResId = Res.string.task_read_article_list,
            descriptionResId = Res.string.task_read_article_list_desc,
        ),
    )

    fun byId(id: String): TaskDescriptor? = builtIns.firstOrNull { it.id == id }
}
