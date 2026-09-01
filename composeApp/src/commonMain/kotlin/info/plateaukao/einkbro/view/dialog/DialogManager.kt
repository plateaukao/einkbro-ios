package info.plateaukao.einkbro.view.dialog

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import info.plateaukao.einkbro.util.blockingString
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.folder_name
import info.plateaukao.einkbro.resources.folder_name_description
import org.jetbrains.compose.resources.StringResource

/**
 * Stand-in for the Android AlertDialog orchestrator. Ok/cancel confirmations,
 * option pickers, and text inputs are published as observable state and
 * rendered by the shared dialog hosts (App.kt) as Compose dialogs; the suspend
 * picker/input calls resolve when the host reports a choice or dismissal.
 */
class DialogManager(private val context: Context = Context()) {

    class OkCancelRequest(
        val title: String?,
        val message: String?,
        val okAction: () -> Unit,
        val cancelAction: (() -> Unit)?,
    )

    class SelectOptionRequest(
        val title: String?,
        val options: List<String>,
        val selectedIndex: Int,
        /** Plain tappable rows: no title bar clutter, no radio buttons
         *  (Android StartPageItemDialog.showPlainListDialog). */
        val plain: Boolean = false,
        val onResult: (Int?) -> Unit,
    )

    /** Multi-choice list (Android AlertDialog.setMultiChoiceItems). [lockedBy]
     *  maps an option index to the index that forces it checked and disabled
     *  while checked (BackupCategory ALL_PREFERENCES -> GPT_SETTINGS). */
    class MultiSelectRequest(
        val title: String,
        val options: List<String>,
        val initiallyChecked: List<Boolean>,
        val lockedBy: Map<Int, Int> = emptyMap(),
        val onResult: (Set<Int>?) -> Unit,
    )

    class TextInputRequest(
        val title: String,
        val description: String?,
        val initialValue: String,
        val onResult: (String?) -> Unit,
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
    ): String? = suspendCancellableCoroutine { cont ->
        pendingTextInput.value = TextInputRequest(
            title = blockingString(titleId),
            description = descriptionId?.let { blockingString(it) },
            initialValue = defaultValue?.toString().orEmpty(),
        ) { result ->
            pendingTextInput.value = null
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { pendingTextInput.value = null }
    }

    suspend fun getSelectedOption(
        titleId: StringResource,
        listSettings: List<StringResource>,
        defaultValue: Int,
    ): Int? = getSelectedOptionWithString(
        titleId, listSettings.map { blockingString(it) }, defaultValue
    )

    suspend fun getSelectedOptionWithString(
        titleId: StringResource,
        listSettings: List<String>,
        defaultValue: Int,
        titleActionIconResId: Any? = null,
        titleActionDescriptionResId: StringResource? = null,
        onTitleAction: (() -> Unit)? = null,
    ): Int? = suspendCancellableCoroutine { cont ->
        pendingSelectOption.value = SelectOptionRequest(
            title = blockingString(titleId),
            options = listSettings,
            selectedIndex = defaultValue,
        ) { result ->
            pendingSelectOption.value = null
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { pendingSelectOption.value = null }
    }

    /** Plain tappable rows (no radio buttons; no title row when [title] is
     *  null) — Android StartPageItemDialog.showPlainListDialog. Cancelling
     *  (tap outside) resumes null. */
    suspend fun getPlainListSelection(
        title: String?,
        names: List<String>,
    ): Int? = suspendCancellableCoroutine { cont ->
        pendingSelectOption.value = SelectOptionRequest(
            title = title,
            options = names,
            selectedIndex = -1,
            plain = true,
        ) { result ->
            pendingSelectOption.value = null
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { pendingSelectOption.value = null }
    }

    /** Multi-choice picker; null when cancelled, the checked indices otherwise. */
    suspend fun getMultiSelection(
        title: String,
        options: List<String>,
        initiallyChecked: List<Boolean> = options.map { true },
        lockedBy: Map<Int, Int> = emptyMap(),
    ): Set<Int>? = suspendCancellableCoroutine { cont ->
        pendingMultiSelect.value = MultiSelectRequest(
            title = title,
            options = options,
            initiallyChecked = initiallyChecked,
            lockedBy = lockedBy,
        ) { result ->
            pendingMultiSelect.value = null
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { pendingMultiSelect.value = null }
    }

    suspend fun getBookmarkFolderName(): String? = getTextInput(
        Res.string.folder_name,
        Res.string.folder_name_description,
        "",
    )

    fun showBookmarkFilePicker(launcher: Any?) {}
    fun showImportBookmarkFilePicker(launcher: Any?) {}
    fun showBackupFilePicker(launcher: Any?) {}
    fun showImportBackupFilePicker(launcher: Any?) {}

    fun showBackupCategoryDialog(onSelected: (Any) -> Unit = {}) {}
    fun showRestoreCategoryDialog(onSelected: (Any) -> Unit = {}) {}

    companion object {
        val pendingOkCancel = mutableStateOf<OkCancelRequest?>(null)
        val pendingSelectOption = mutableStateOf<SelectOptionRequest?>(null)
        val pendingTextInput = mutableStateOf<TextInputRequest?>(null)
        val pendingMultiSelect = mutableStateOf<MultiSelectRequest?>(null)
    }
}
