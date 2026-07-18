package info.plateaukao.einkbro.util

/**
 * Minimal HMAC seam for the translation providers (Papago signs requests with
 * HMAC-MD5; its image OCR endpoint with HMAC-SHA1). CommonCrypto on iOS.
 */
expect object Crypto {
    fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray
    fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray
    fun md5Hex(data: ByteArray): String

    /** Uppercase hex SHA-256 (Edge-TTS Sec-MS-GEC token, parity Phase L). */
    fun sha256Hex(data: ByteArray): String

    /** Raw SHA-256 digest (PKCE code challenge for Google Drive sync). */
    fun sha256(data: ByteArray): ByteArray

    /** Cryptographically secure random bytes (PKCE verifier/state tokens). */
    fun randomBytes(count: Int): ByteArray
}
