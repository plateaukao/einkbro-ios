package info.plateaukao.einkbro.util

/**
 * Minimal digest seam for Edge-TTS tokens and Google Drive PKCE. CommonCrypto
 * on iOS. (The HMAC-MD5 / HMAC-SHA1 entries the Papago providers needed went
 * away with them.)
 */
expect object Crypto {
    /** Uppercase hex SHA-256 (Edge-TTS Sec-MS-GEC token, parity Phase L). */
    fun sha256Hex(data: ByteArray): String

    /** Raw SHA-256 digest (PKCE code challenge for Google Drive sync). */
    fun sha256(data: ByteArray): ByteArray

    /** Cryptographically secure random bytes (PKCE verifier/state tokens). */
    fun randomBytes(count: Int): ByteArray
}
