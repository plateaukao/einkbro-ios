package info.plateaukao.einkbro.util

/**
 * Minimal file persistence for exported artifacts (saved web archives, PDFs).
 * Files land under the app's Documents directory on iOS.
 */
expect object FileStore {
    /** Writes [bytes] to Documents/[subDir]/[fileName]; returns the full path. */
    fun writeBytes(subDir: String, fileName: String, bytes: ByteArray): String?

    /** Ensures Documents/[subDir] exists and returns its absolute path. */
    fun dirPath(subDir: String): String?

    /** Absolute path of the Documents directory of *this* install. */
    fun documentsPath(): String?

    /** Reads the whole file at [path], or null if it can't be read. */
    fun readBytes(path: String): ByteArray?

    /** Writes [bytes] to an absolute [path] (overwrite); returns the path or null. */
    fun writeToPath(path: String, bytes: ByteArray): String?

    fun exists(path: String): Boolean
    fun delete(path: String)

    /** Opens the file in the system share / preview sheet (UIActivityViewController). */
    fun share(path: String)
}

// iOS mints a fresh UUID for the app's data container on every (re)install, so
// an absolute path recorded by an earlier install stops resolving even though
// the file itself moved along with the container. Anything that outlives the
// process — DB rows, preferences — must therefore persist [storedPathFor] and
// read it back through [resolveStoredPath].
private const val CONTAINER_MARKER = "/Containers/Data/Application/"
private const val DOCUMENTS_MARKER = "/Documents/"

/** Documents-relative form of [absolutePath] — the form that is safe to persist. */
fun storedPathFor(absolutePath: String): String {
    val documents = FileStore.documentsPath() ?: return absolutePath
    return absolutePath.removePrefix("$documents/")
}

/**
 * Turns a persisted path back into one valid for this install: a relative path
 * is re-rooted at the current Documents directory, and an absolute path left
 * behind by an earlier container is re-rooted by its Documents-relative tail.
 * Paths outside the container (a Files-app hand-off) and opaque URIs (the
 * Android `content://` entries a restored backup carries) pass through.
 */
fun resolveStoredPath(storedPath: String): String {
    if (storedPath.isBlank() || storedPath.contains("://")) return storedPath
    val documents = FileStore.documentsPath() ?: return storedPath
    if (!storedPath.startsWith("/")) return "$documents/$storedPath"
    if (FileStore.exists(storedPath) || !storedPath.contains(CONTAINER_MARKER)) return storedPath
    val tail = storedPath.substringAfter(DOCUMENTS_MARKER, "")
    return if (tail.isBlank()) storedPath else "$documents/$tail"
}

/** Turns an arbitrary title into a safe, bounded file-name stem. */
fun sanitizeFileName(title: String, fallback: String = "page"): String {
    val cleaned = title.map { c ->
        if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_'
    }.joinToString("").trim('_')
    return cleaned.ifBlank { fallback }.take(80)
}
