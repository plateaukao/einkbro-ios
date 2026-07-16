package info.plateaukao.einkbro.util

/**
 * Minimal file persistence for exported artifacts (saved web archives, PDFs).
 * Files land under the app's Documents directory on iOS.
 */
expect object FileStore {
    /** Writes [bytes] to Documents/[subDir]/[fileName]; returns the full path. */
    fun writeBytes(subDir: String, fileName: String, bytes: ByteArray): String?

    fun exists(path: String): Boolean
    fun delete(path: String)

    /** Opens the file in the system share / preview sheet (UIActivityViewController). */
    fun share(path: String)
}

/** Turns an arbitrary title into a safe, bounded file-name stem. */
fun sanitizeFileName(title: String, fallback: String = "page"): String {
    val cleaned = title.map { c ->
        if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_'
    }.joinToString("").trim('_')
    return cleaned.ifBlank { fallback }.take(80)
}
