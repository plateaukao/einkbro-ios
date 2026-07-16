package info.plateaukao.einkbro.util

/**
 * Minimal HMAC seam for the translation providers (Papago signs requests with
 * HMAC-MD5; its image OCR endpoint with HMAC-SHA1). CommonCrypto on iOS.
 */
expect object Crypto {
    fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray
    fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray
    fun md5Hex(data: ByteArray): String
}
