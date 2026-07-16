package info.plateaukao.einkbro.view.dialog

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import info.plateaukao.einkbro.util.blockingString
import org.jetbrains.compose.resources.StringResource

/**
 * Stand-in for the Android AlertDialog orchestrator. Ok/cancel confirmations
 * are published as observable state and rendered by the catalog host as a
 * Compose dialog; input/selection flows resolve to null (cancelled).
 */
class DialogManager(private val context: Context = Context()) {

    class OkCancelRequest(
        val title: String?,
        val message: String?,
        val okAction: () -> Unit,
        val cancelAction: (() -> Unit)?,
    )

    fun showOkCancelDialog(
        title: String? = null,
        messageResId: StringResource? = null,
        message: String? = null,
        view: Any? = null,
        okAction: () -> Unit,
        cancelAction: (() -> Unit)? = null,
        showInCenter: Boolean = false,
        showNegativeButton: Boolean = true,
    ) {
        pendingOkCancel.value = OkCancelRequest(
            title = title,
            message = message ?: messageResId?.let { blockingString(it) },
            okAction = okAction,
            cancelAction = cancelAction,
        )
    }

    fun showRestartConfirmDialog() {
        showOkCancelDialog(
            message = "Restart the app to apply this change? (not applicable in the iOS UI catalog)",
            okAction = {},
        )
    }

    suspend fun <T> getTextInput(
        titleId: StringResource,
        descriptionId: StringResource? = null,
        defaultValue: T,
    ): String? = null

    suspend fun getSelectedOption(
        titleId: StringResource,
        listSettings: List<StringResource>,
        defaultValue: Int,
    ): Int? = null

    suspend fun getSelectedOptionWithString(
        titleId: StringResource,
        listSettings: List<String>,
        defaultValue: Int,
        titleActionIconResId: Any? = null,
        titleActionDescriptionResId: StringResource? = null,
        onTitleAction: (() -> Unit)? = null,
    ): Int? = null

    suspend fun getBookmarkFolderName(): String? = null

    fun showBookmarkFilePicker(launcher: Any?) {}
    fun showImportBookmarkFilePicker(launcher: Any?) {}
    fun showBackupFilePicker(launcher: Any?) {}
    fun showImportBackupFilePicker(launcher: Any?) {}

    fun showBackupCategoryDialog(onSelected: (Any) -> Unit = {}) {}
    fun showRestoreCategoryDialog(onSelected: (Any) -> Unit = {}) {}

    companion object {
        val pendingOkCancel = mutableStateOf<OkCancelRequest?>(null)
    }
}
