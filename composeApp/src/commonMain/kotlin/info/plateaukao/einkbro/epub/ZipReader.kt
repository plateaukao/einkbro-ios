package info.plateaukao.einkbro.epub

/**
 * Minimal ZIP reader — the counterpart to [ZipWriter]. STORED entries are read
 * directly; DEFLATE entries are inflated through [inflateRaw] so Android-made
 * backup zips (ZipOutputStream deflates everything) restore on iOS. Entries
 * using any other method are skipped, so an exotic third-party EPUB simply
 * yields no usable EinkBro sidecar and append falls back to a new book.
 * Entry data is located via the central directory, which also keeps sizes
 * correct for streamed zips that use data descriptors (Android's do).
 */
object ZipReader {

    /** name -> bytes for every readable entry, or null if the archive is unreadable. */
    fun read(data: ByteArray): Map<String, ByteArray>? {
        val eocd = findEocd(data) ?: return null
        val count = u16(data, eocd + 10)
        var p = u32(data, eocd + 16)
        val map = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (p + 46 > data.size || u32(data, p) != CENTRAL_SIG) return map.ifEmpty { null }
            val method = u16(data, p + 10)
            val compSize = u32(data, p + 20)
            val uncompSize = u32(data, p + 24)
            val nameLen = u16(data, p + 28)
            val extraLen = u16(data, p + 30)
            val commentLen = u16(data, p + 32)
            val localOffset = u32(data, p + 42)
            val name = data.decodeToString(p + 46, p + 46 + nameLen)
            if ((method == 0 || method == 8) && localOffset + 30 <= data.size) {
                val lhNameLen = u16(data, localOffset + 26)
                val lhExtraLen = u16(data, localOffset + 28)
                val start = localOffset + 30 + lhNameLen + lhExtraLen
                if (start + compSize <= data.size) {
                    val raw = data.copyOfRange(start, start + compSize)
                    if (method == 0) map[name] = raw
                    else inflateRaw(raw, uncompSize)?.let { map[name] = it }
                }
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return map
    }

    private fun findEocd(data: ByteArray): Int? {
        // No archive comment is written, so the EOCD is the trailing 22 bytes;
        // scan back a little regardless to be safe.
        var i = data.size - 22
        val min = maxOf(0, data.size - 22 - 512)
        while (i >= min) {
            if (i + 4 <= data.size && u32(data, i) == EOCD_SIG) return i
            i--
        }
        return null
    }

    private fun u16(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8) or
            ((d[o + 2].toInt() and 0xFF) shl 16) or ((d[o + 3].toInt() and 0xFF) shl 24)

    private const val CENTRAL_SIG = 0x02014b50
    private const val EOCD_SIG = 0x06054b50
}
