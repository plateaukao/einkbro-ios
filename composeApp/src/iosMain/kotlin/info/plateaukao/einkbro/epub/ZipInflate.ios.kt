package info.plateaukao.einkbro.epub

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

// platform.Compression is not bridged by Kotlin/Native, but platform.zlib is;
// windowBits -15 selects raw DEFLATE (no zlib header), the payload format of a
// zip method-8 entry.
@OptIn(ExperimentalForeignApi::class)
actual fun inflateRaw(data: ByteArray, uncompressedSize: Int): ByteArray? {
    if (uncompressedSize <= 0 || data.isEmpty()) return null
    val out = ByteArray(uncompressedSize)
    var ok = false
    data.usePinned { src ->
        out.usePinned { dst ->
            memScoped {
                val strm = alloc<z_stream>()
                strm.next_in = src.addressOf(0).reinterpret()
                strm.avail_in = data.size.toUInt()
                strm.next_out = dst.addressOf(0).reinterpret()
                strm.avail_out = uncompressedSize.toUInt()
                if (inflateInit2_(strm.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt()) != 0) {
                    return@memScoped
                }
                val status = inflate(strm.ptr, Z_FINISH)
                ok = status == Z_STREAM_END && strm.total_out.toInt() == uncompressedSize
                inflateEnd(strm.ptr)
            }
        }
    }
    return if (ok) out else null
}
