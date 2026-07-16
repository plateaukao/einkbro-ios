package info.plateaukao.einkbro.epub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One embedded image resource inside a chapter (already uniquely named). */
class EpubImage(val name: String, val mediaType: String, val data: ByteArray)

/**
 * One chapter: [bodyXhtml] is well-formed XHTML (an XMLSerializer fragment) whose
 * `<img src>` values reference this book's [images] names.
 */
class EpubChapter(
    val title: String,
    val bodyXhtml: String,
    val images: List<EpubImage> = emptyList(),
)

class EpubBook(
    val title: String,
    val author: String,
    val identifier: String,
    val chapters: List<EpubChapter>,
    val language: String = "en",
)

/**
 * Assembles an [EpubBook] into EPUB 2 bytes with a hand-rolled OPF + NCX and a
 * pure-Kotlin [ZipWriter] — no epublib, no platform zip. The layout is the
 * standard OCF: `mimetype`, `META-INF/container.xml`, and an `OEBPS/` folder
 * holding `content.opf`, `toc.ncx`, the chapter XHTML, and image resources.
 */
object EpubBuilder {

    /** Marker identifier so EinkBro can recognize its own EPUBs (editable ToC). */
    const val EINKBRO_MARKER = "EinkBro"

    /** Sidecar path holding the book model, letting append rebuild without an OPF parser. */
    const val META_HREF = "einkbro_meta.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun build(book: EpubBook): ByteArray {
        val zip = ZipWriter()
        // mimetype MUST be first and stored (OCF requirement).
        zip.addStored("mimetype", "application/epub+zip")
        zip.addStored("META-INF/container.xml", CONTAINER_XML)
        zip.addStored("OEBPS/content.opf", opf(book))
        zip.addStored("OEBPS/toc.ncx", ncx(book))
        zip.addStored("OEBPS/$META_HREF", metaJson(book))
        book.chapters.forEachIndexed { i, chapter ->
            zip.addStored("OEBPS/${chapterHref(i)}", chapterXhtml(chapter))
            chapter.images.forEach { img -> zip.addStored("OEBPS/${img.name}", img.data) }
        }
        return zip.build()
    }

    /** Serializes the book (minus image bytes) for [EpubReader] to reload on append. */
    private fun metaJson(book: EpubBook): String {
        val chapters = book.chapters.map { ch ->
            MetaChapter(ch.title, ch.bodyXhtml, ch.images.map { MetaImage(it.name, it.mediaType) })
        }
        val meta = MetaBook(book.title, book.author, book.identifier, book.language, chapters)
        return json.encodeToString(MetaBook.serializer(), meta)
    }

    /** Parses the sidecar written by [metaJson]. */
    internal fun parseMeta(text: String): MetaBook = json.decodeFromString(MetaBook.serializer(), text)

    @Serializable
    internal class MetaBook(
        val title: String,
        val author: String,
        val identifier: String,
        val language: String,
        val chapters: List<MetaChapter>,
    )

    @Serializable
    internal class MetaChapter(val title: String, val bodyXhtml: String, val images: List<MetaImage>)

    @Serializable
    internal class MetaImage(val name: String, val mediaType: String)

    private fun chapterHref(index: Int) = "chapter${index + 1}.xhtml"

    private fun chapterXhtml(chapter: EpubChapter): String {
        val title = escape(chapter.title)
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"/><title>$title</title></head>
<body><h1>$title</h1>
${chapter.bodyXhtml}
</body>
</html>"""
    }

    private fun opf(book: EpubBook): String {
        val manifest = StringBuilder()
        manifest.append("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""").append('\n')
        book.chapters.forEachIndexed { i, chapter ->
            manifest.append("""    <item id="chapter${i + 1}" href="${chapterHref(i)}" media-type="application/xhtml+xml"/>""").append('\n')
            chapter.images.forEach { img ->
                manifest.append("""    <item id="${imageId(img.name)}" href="${img.name}" media-type="${img.mediaType}"/>""").append('\n')
            }
        }
        val spine = StringBuilder()
        book.chapters.indices.forEach { i ->
            spine.append("""    <itemref idref="chapter${i + 1}"/>""").append('\n')
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:title>${escape(book.title)}</dc:title>
    <dc:creator opf:role="aut">${escape(book.author)}</dc:creator>
    <dc:language>${escape(book.language)}</dc:language>
    <dc:identifier id="bookid">${escape(book.identifier)}</dc:identifier>
    <dc:identifier opf:scheme="einkbro-generator">$EINKBRO_MARKER</dc:identifier>
  </metadata>
  <manifest>
${manifest.toString().trimEnd('\n')}
  </manifest>
  <spine toc="ncx">
${spine.toString().trimEnd('\n')}
  </spine>
</package>"""
    }

    private fun ncx(book: EpubBook): String {
        val navPoints = StringBuilder()
        book.chapters.forEachIndexed { i, chapter ->
            navPoints.append(
                """    <navPoint id="np${i + 1}" playOrder="${i + 1}"><navLabel><text>${escape(chapter.title)}</text></navLabel><content src="${chapterHref(i)}"/></navPoint>"""
            ).append('\n')
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="${escape(book.identifier)}"/></head>
  <docTitle><text>${escape(book.title)}</text></docTitle>
  <navMap>
${navPoints.toString().trimEnd('\n')}
  </navMap>
</ncx>"""
    }

    /** Manifest item id for an image path (e.g. images/c0_img1.jpg -> img_c0_img1_jpg). */
    private fun imageId(name: String): String =
        "img_" + name.removePrefix("images/").replace('.', '_').replace('/', '_')

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    fun mediaTypeForExt(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private const val CONTAINER_XML = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
}
