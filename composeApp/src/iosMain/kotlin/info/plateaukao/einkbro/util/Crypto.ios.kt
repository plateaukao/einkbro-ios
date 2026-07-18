package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgMD5
import platform.CoreCrypto.kCCHmacAlgSHA1
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual object Crypto {

    private fun hmac(algorithm: UInt, digestLength: Int, key: ByteArray, data: ByteArray): ByteArray {
        val result = ByteArray(digestLength)
        // Empty arrays can't be pinned to a real address; CommonCrypto accepts
        // a zero length with any non-null pointer, so pad with one throwaway byte.
        val safeKey = if (key.isEmpty()) ByteArray(1) else key
        val safeData = if (data.isEmpty()) ByteArray(1) else data
        safeKey.usePinned { pinnedKey ->
            safeData.usePinned { pinnedData ->
                result.usePinned { pinnedResult ->
                    CCHmac(
                        algorithm,
                        pinnedKey.addressOf(0), key.size.toULong(),
                        pinnedData.addressOf(0), data.size.toULong(),
                        pinnedResult.addressOf(0),
                    )
                }
            }
        }
        return result
    }

    actual fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray =
        hmac(kCCHmacAlgMD5, CC_MD5_DIGEST_LENGTH, key, data)

    actual fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray =
        hmac(kCCHmacAlgSHA1, CC_SHA1_DIGEST_LENGTH, key, data)

    actual fun md5Hex(data: ByteArray): String {
        val result = ByteArray(CC_MD5_DIGEST_LENGTH)
        val safeData = if (data.isEmpty()) ByteArray(1) else data
        safeData.usePinned { pinned ->
            result.usePinned { pinnedResult ->
                CC_MD5(pinned.addressOf(0), data.size.toUInt(), pinnedResult.addressOf(0).reinterpret())
            }
        }
        return result.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

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
