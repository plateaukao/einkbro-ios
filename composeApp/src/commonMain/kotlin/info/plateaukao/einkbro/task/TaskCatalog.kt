package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.task_read_article_list
import info.plateaukao.einkbro.resources.task_read_article_list_desc
import info.plateaukao.einkbro.task.tasks.ReadArticleListTask
import org.jetbrains.compose.resources.StringResource

/**
 * Registry of built-in task templates. Add new entries here and they appear
 * automatically in the task picker dialog.
 *
 * Free-form / custom tasks are NOT in this catalog — they are constructed on the
 * fly from a user prompt and handled separately by the browser host.
 */
object TaskCatalog {
    val builtIns: List<TaskDescriptor> = listOf(
        TaskDescriptor(
            id = "read_article_list",
            displayNameResId = Res.string.task_read_article_list,
            descriptionResId = Res.string.task_read_article_list_desc,
        ) { ReadArticleListTask() },
    )

    fun byId(id: String): TaskDescriptor? = builtIns.firstOrNull { it.id == id }
}

data class TaskDescriptor(
    val id: String,
    val displayNameResId: StringResource,
    val descriptionResId: StringResource,
    val factory: () -> BrowserTask,
)
