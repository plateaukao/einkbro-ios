package info.plateaukao.einkbro.util

/** Minimal android.net.Uri stand-in: just enough for host extraction. */
class Uri private constructor(private val raw: String) {
    val host: String?
        get() {
            val afterScheme = raw.substringAfter("://", "")
            if (afterScheme.isEmpty()) return null
            val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val noUserInfo = authority.substringAfterLast('@')
            val host = noUserInfo.substringBefore(':')
            return host.ifEmpty { null }
        }

    override fun toString(): String = raw

    companion object {
        fun parse(uriString: String): Uri = Uri(uriString)
    }
}
