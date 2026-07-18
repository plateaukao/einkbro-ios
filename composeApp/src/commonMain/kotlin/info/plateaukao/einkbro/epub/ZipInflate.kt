package info.plateaukao.einkbro.epub

/**
 * Inflates one raw-DEFLATE block (a zip method-8 entry body, no zlib header)
 * into exactly [uncompressedSize] bytes, or null when the data is corrupt.
 * Needed to read Android-made backup zips, whose ZipOutputStream deflates
 * every entry; the iOS actual uses Apple's libcompression.
 */
expect fun inflateRaw(data: ByteArray, uncompressedSize: Int): ByteArray?
