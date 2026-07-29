package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual object Crypto {

    actual fun sha256Hex(data: ByteArray): String =
        sha256(data).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }.uppercase()

    actual fun sha256(data: ByteArray): ByteArray {
        val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
        val safeData = if (data.isEmpty()) ByteArray(1) else data
        safeData.usePinned { pinned ->
            result.usePinned { pinnedResult ->
                CC_SHA256(pinned.addressOf(0), data.size.toUInt(), pinnedResult.addressOf(0).reinterpret())
            }
        }
        return result
    }

    actual fun randomBytes(count: Int): ByteArray {
        val result = ByteArray(count)
        result.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, count.toULong(), pinned.addressOf(0))
        }
        return result
    }
}
