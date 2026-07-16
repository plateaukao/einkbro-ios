package info.plateaukao.einkbro.epub

/**
 * Reconstructs an [EpubBook] from an EinkBro-written EPUB so a chapter can be
 * appended and the whole book rewritten (matching Android's epub4j re-serialize
 * model). It reads the `einkbro_meta.json` sidecar for chapter titles/bodies and
 * pulls each image's bytes from its stored zip entry. Returns null for a
 * non-EinkBro or compressed EPUB (append then falls back to a new book).
 */
object EpubReader {

    fun read(epubBytes: ByteArray): EpubBook? {
        val entries = ZipReader.read(epubBytes) ?: return null
        val metaBytes = entries["OEBPS/${EpubBuilder.META_HREF}"] ?: return null
        val meta = runCatching { EpubBuilder.parseMeta(metaBytes.decodeToString()) }.getOrNull()
            ?: return null
        val chapters = meta.chapters.map { ch ->
            val images = ch.images.mapNotNull { img ->
                val data = entries["OEBPS/${img.name}"] ?: return@mapNotNull null
                EpubImage(img.name, img.mediaType, data)
            }
            EpubChapter(ch.title, ch.bodyXhtml, images)
        }
        return EpubBook(meta.title, meta.author, meta.identifier, chapters, meta.language)
    }
}
