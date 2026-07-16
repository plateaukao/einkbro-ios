package info.plateaukao.einkbro.epub

/**
 * Minimal ZIP reader for STORED (uncompressed) entries — the counterpart to
 * [ZipWriter]. It only needs to read back EinkBro's own EPUBs (all stored) to
 * support "append a chapter"; entries using any compression method are skipped,
 * so a deflated third-party EPUB simply yields no usable EinkBro sidecar and
 * append falls back to creating a new book.
 */
object ZipReader {

    /** name -> bytes for every stored entry, or null if the archive is unreadable. */
    fun read(data: ByteArray): Map<String, ByteArray>? {
        val eocd = findEocd(data) ?: return null
        val count = u16(data, eocd + 10)
        var p = u32(data, eocd + 16)
        val map = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (p + 46 > data.size || u32(data, p) != CENTRAL_SIG) return map.ifEmpty { null }
            val method = u16(data, p + 10)
            val compSize = u32(data, p + 20)
            val nameLen = u16(data, p + 28)
            val extraLen = u16(data, p + 30)
            val commentLen = u16(data, p + 32)
            val localOffset = u32(data, p + 42)
            val name = data.decodeToString(p + 46, p + 46 + nameLen)
            if (method == 0 && localOffset + 30 <= data.size) {
                val lhNameLen = u16(data, localOffset + 26)
                val lhExtraLen = u16(data, localOffset + 28)
                val start = localOffset + 30 + lhNameLen + lhExtraLen
                if (start + compSize <= data.size) {
                    map[name] = data.copyOfRange(start, start + compSize)
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
