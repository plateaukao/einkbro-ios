package info.plateaukao.einkbro.epub

import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.util.FileStore
import info.plateaukao.einkbro.util.sanitizeFileName
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A page captured for EPUB export (parsed from get_epub_chapter.js). */
@Serializable
class CapturedChapter(
    val title: String = "",
    val xhtml: String = "",
    val images: List<CapturedImage> = emptyList(),
    val error: String? = null,
)

@Serializable
class CapturedImage(val name: String, val url: String)

sealed class ExportResult {
    data class Success(val path: String, val bookTitle: String) : ExportResult()
    data class Failure(val message: String) : ExportResult()
}

/**
 * Turns a captured page into an EPUB chapter and writes a new book — or appends
 * to an existing EinkBro EPUB and rewrites it (matching Android's re-serialize
 * model). Image bytes are fetched at export time over Ktor and embedded.
 */
class EpubExporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseCapture(raw: String): CapturedChapter? =
        runCatching { json.decodeFromString(CapturedChapter.serializer(), raw) }.getOrNull()

    /**
     * @param appendToPath existing EinkBro .epub to append to, or null for a new book.
     * @param onProgress 0..100 (image download fills 10..90).
     */
    suspend fun export(
        capture: CapturedChapter,
        bookName: String,
        chapterTitle: String,
        pageUrl: String,
        appendToPath: String?,
        onProgress: (Int) -> Unit,
    ): ExportResult {
        onProgress(5)
        // Where this chapter lands decides its image namespace (avoids collisions
        // across chapters that both start numbering images at 0).
        val existingBook = appendToPath
            ?.let { FileStore.readBytes(it) }
            ?.let { EpubReader.read(it) }
        val chapterIndex = existingBook?.chapters?.size ?: 0
        val prefix = "images/c${chapterIndex}_"

        // Fetch + rename this chapter's images; rewrite the body's src to match.
        val images = mutableListOf<EpubImage>()
        var body = capture.xhtml.replace("\"images/", "\"$prefix")
        val total = capture.images.size
        capture.images.forEachIndexed { i, img ->
            val newName = img.name.replace("images/", prefix)
            val bytes = runCatching { HttpClientProvider.client.get(img.url).body<ByteArray>() }
                .getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val ext = newName.substringAfterLast('.', "jpg")
                images.add(EpubImage(newName, EpubBuilder.mediaTypeForExt(ext), bytes))
            } else {
                // Drop a failed image so the EPUB has no dangling reference.
                body = body.replace("src=\"$newName\"", "src=\"\"")
            }
            if (total > 0) onProgress(10 + (i + 1) * 80 / total)
        }
        onProgress(90)

        val chapter = EpubChapter(chapterTitle.ifBlank { "Chapter" }, body, images)
        val book = if (existingBook != null) {
            EpubBook(
                title = existingBook.title,
                author = existingBook.author,
                identifier = existingBook.identifier,
                chapters = existingBook.chapters + chapter,
                language = existingBook.language,
            )
        } else {
            EpubBook(
                title = bookName.ifBlank { "EinkBro book" },
                author = hostOf(pageUrl),
                identifier = "urn:uuid:einkbro-${(bookName + chapterTitle).hashCode().toUInt().toString(16)}",
                chapters = listOf(chapter),
            )
        }

        val bytes = EpubBuilder.build(book)
        val path = if (appendToPath != null && existingBook != null) {
            FileStore.writeToPath(appendToPath, bytes)
        } else {
            FileStore.writeBytes("epub", "${sanitizeFileName(book.title, "book")}.epub", bytes)
        } ?: return ExportResult.Failure("write failed")

        onProgress(100)
        FileStore.share(path)
        return ExportResult.Success(path, book.title)
    }

    private fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        return noScheme.substringBefore('/').substringBefore(':').ifBlank { "EinkBro" }
    }
}
