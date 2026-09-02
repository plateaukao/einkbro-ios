package info.plateaukao.einkbro.util

/**
 * Presents the system open-file picker and returns the chosen file's name and
 * bytes (parity Phase J: backup / bookmark import). [onResult] is called only on
 * a successful pick; cancelling does nothing. The iosMain actual uses
 * UIDocumentPickerViewController with asCopy so no security-scoped access is
 * needed.
 */
/** What the system document picker offers: any file, or font files only. */
enum class PickKind { Any, Font }

expect object FilePicker {
    fun pick(kind: PickKind = PickKind.Any, onResult: (name: String, bytes: ByteArray) -> Unit)
}
