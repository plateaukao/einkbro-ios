package info.plateaukao.einkbro.browser

interface DomainInterface {
    suspend fun getDomains(): List<String>
    suspend fun addDomain(domain: String)
    suspend fun deleteDomain(domain: String)
    suspend fun deleteAllDomains()
}

/**
 * In-memory stand-in for the Android BaseWebConfig (Room-backed per-feature
 * domain whitelist + hosts file). Keeps the small API the UI layer touches:
 * the [DomainInterface] CRUD plus [isWhite]/[isAd] style checks.
 */
abstract class BaseWebConfig(initialDomains: List<String>) : DomainInterface {

    private val whitelist: MutableSet<String> = LinkedHashSet(initialDomains)
    protected val hosts: MutableSet<String> = mutableSetOf()

    fun isWhite(url: String): Boolean = whitelist.any { url.contains(it) }

    fun isAd(url: String): Boolean = hosts.any { url.contains(it) }

    override suspend fun getDomains(): List<String> = whitelist.toList()

    override suspend fun addDomain(domain: String) {
        whitelist.add(domain)
    }

    override suspend fun deleteDomain(domain: String) {
        whitelist.remove(domain)
    }

    override suspend fun deleteAllDomains() {
        whitelist.clear()
    }
}
