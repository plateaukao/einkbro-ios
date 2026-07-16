package info.plateaukao.einkbro.util

/**
 * Presents the system open-file picker and returns the chosen file's name and
 * bytes (parity Phase J: backup / bookmark import). [onResult] is called only on
 * a successful pick; cancelling does nothing. The iosMain actual uses
 * UIDocumentPickerViewController with asCopy so no security-scoped access is
 * needed.
 */
expect object FilePicker {
    fun pick(onResult: (name: String, bytes: ByteArray) -> Unit)
}
