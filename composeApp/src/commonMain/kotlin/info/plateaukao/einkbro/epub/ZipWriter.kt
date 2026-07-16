package info.plateaukao.einkbro.epub

/**
 * Minimal pure-Kotlin ZIP writer producing STORED (uncompressed) entries — no
 * external zip/deflate dependency, which keeps EPUB assembly in commonMain and
 * off any platform API. EPUB readers accept stored entries; the only cost is
 * file size (fine for article-sized books).
 *
 * The EPUB OCF spec requires the `mimetype` entry to be first and stored, which
 * a stored-only writer satisfies for free.
 */
class ZipWriter {
    private val out = ByteSink()
    private val entries = mutableListOf<Entry>()

    private class Entry(val name: String, val crc: Int, val size: Int, val offset: Int)

    /** Appends one uncompressed entry. Call in the order you want them stored. */
    fun addStored(name: String, data: ByteArray) {
        val crc = Crc32.compute(data)
        val offset = out.size
        val nameBytes = name.encodeToByteArray()
        out.u32(LOCAL_SIG)
        out.u16(20); out.u16(0); out.u16(0)          // version needed, flags, method=stored
        out.u16(DOS_TIME); out.u16(DOS_DATE)
        out.u32(crc); out.u32(data.size); out.u32(data.size)
        out.u16(nameBytes.size); out.u16(0)          // name len, extra len
        out.bytes(nameBytes)
        out.bytes(data)
        entries.add(Entry(name, crc, data.size, offset))
    }

    fun addStored(name: String, text: String) = addStored(name, text.encodeToByteArray())

    /** Writes the central directory + EOCD and returns the complete archive. */
    fun build(): ByteArray {
        val cdStart = out.size
        for (e in entries) {
            val nameBytes = e.name.encodeToByteArray()
            out.u32(CENTRAL_SIG)
            out.u16(20); out.u16(20); out.u16(0); out.u16(0)   // made-by, needed, flags, method
            out.u16(DOS_TIME); out.u16(DOS_DATE)
            out.u32(e.crc); out.u32(e.size); out.u32(e.size)
            out.u16(nameBytes.size); out.u16(0); out.u16(0)    // name, extra, comment lengths
            out.u16(0); out.u16(0); out.u32(0)                 // disk#, internal attrs, external attrs
            out.u32(e.offset)
            out.bytes(nameBytes)
        }
        val cdSize = out.size - cdStart
        out.u32(EOCD_SIG)
        out.u16(0); out.u16(0)                                 // disk numbers
        out.u16(entries.size); out.u16(entries.size)
        out.u32(cdSize); out.u32(cdStart)
        out.u16(0)                                             // comment length
        return out.toByteArray()
    }

    private companion object {
        const val LOCAL_SIG = 0x04034b50
        const val CENTRAL_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        // Fixed valid DOS timestamp (2020-01-01 00:00); EPUB ignores entry times.
        const val DOS_TIME = 0
        const val DOS_DATE = (40 shl 9) or (1 shl 5) or 1
    }
}

/** Growable little-endian byte buffer. */
private class ByteSink {
    private var buf = ByteArray(1024)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        var cap = buf.size
        while (cap < size + extra) cap = cap shl 1
        buf = buf.copyOf(cap)
    }

    fun byte(v: Int) {
        ensure(1)
        buf[size++] = (v and 0xFF).toByte()
    }

    fun u16(v: Int) {
        byte(v); byte(v ushr 8)
    }

    fun u32(v: Int) {
        byte(v); byte(v ushr 8); byte(v ushr 16); byte(v ushr 24)
    }

    fun bytes(data: ByteArray) {
        ensure(data.size)
        data.copyInto(buf, size)
        size += data.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

/** Table-driven CRC-32 (IEEE) — needed for each ZIP entry. */
object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    fun compute(data: ByteArray): Int {
        var crc = 0.inv()
        for (b in data) crc = table[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }
}
