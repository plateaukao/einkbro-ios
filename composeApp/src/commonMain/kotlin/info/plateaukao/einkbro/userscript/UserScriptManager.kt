package info.plateaukao.einkbro.userscript

import info.plateaukao.einkbro.data.remote.HttpClientProvider
import info.plateaukao.einkbro.database.AppDatabase
import info.plateaukao.einkbro.database.UserScript
import info.plateaukao.einkbro.database.UserScriptValue
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A userscript paired with its parsed metadata. */
data class ParsedUserScript(
    val script: UserScript,
    val metadata: UserScriptMetadata,
)

/** Outcome of [UserScriptManager.checkAndUpdate]. */
sealed class UpdateResult {
    /** A newer version was downloaded and installed. */
    data class Updated(val from: String, val to: String) : UpdateResult()

    /** The remote version was not newer than the installed one. */
    object UpToDate : UpdateResult()

    /** The script has no `@updateURL`/`@downloadURL` and no install URL to check. */
    object NoSource : UpdateResult()

    /** The check or download failed (network error, empty response, etc.). */
    data class Failed(val message: String) : UpdateResult()
}

/**
 * Room-backed userscript store + injection builder (parity Phase H). Mirrors the
 * Android UserScriptManager: script rows in `user_scripts`, GM_setValue storage
 * in `user_script_values`, metadata parsed on demand from each row's `code`.
 *
 * The script body is kept inline in the DB `code` column. Android moves bodies
 * to files to dodge its 2 MB `CursorWindow` limit; the iOS SQLite driver has no
 * such cursor, so inline storage is simpler and safe.
 *
 * Injection (see [buildInjectionJs]) is pushed once per page load via
 * `evaluateJavascript` — WKWebView's K/N navigation delegate can't expose a
 * document-start hook, so `@run-at` collapses to page-finished, which is where
 * the runtime evaluates every matching script.
 */
class UserScriptManager(database: AppDatabase) {

    private val scriptDao = database.userScriptDao()
    private val valueDao = database.userScriptValueDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var scripts: List<ParsedUserScript> = emptyList()
        private set

    suspend fun reload() {
        scripts = scriptDao.getAll().map { ParsedUserScript(it, UserScriptMetadata.parse(it.code)) }
    }

    fun getById(id: Long): ParsedUserScript? = scripts.firstOrNull { it.script.id == id }

    suspend fun add(code: String, sourceUrl: String? = null): Long {
        val meta = UserScriptMetadata.parse(code)
        val id = scriptDao.insert(
            UserScript(
                name = meta.name,
                enabled = true,
                code = code,
                sourceUrl = sourceUrl ?: meta.downloadUrl.ifBlank { null },
                order = scripts.size,
            )
        )
        reload()
        return id
    }

    suspend fun update(script: UserScript) {
        scriptDao.update(script.copy(name = UserScriptMetadata.parse(script.code).name))
        reload()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val row = scriptDao.getById(id) ?: return
        scriptDao.update(row.copy(enabled = enabled))
        reload()
    }

    suspend fun delete(id: Long) {
        valueDao.deleteAllForScript(id)
        scriptDao.deleteById(id)
        reload()
    }

    /** Fetches `@updateURL`/`@downloadURL` (or the install URL) and installs a newer version. */
    suspend fun checkAndUpdate(id: Long): UpdateResult {
        val parsed = getById(id) ?: return UpdateResult.Failed("script not found")
        val script = parsed.script
        val meta = parsed.metadata
        val checkUrl = meta.updateUrl.ifBlank { meta.downloadUrl }.ifBlank { script.sourceUrl.orEmpty() }
        if (checkUrl.isBlank()) return UpdateResult.NoSource
        return try {
            val remoteMetaText = HttpClientProvider.client.get(checkUrl).bodyAsText()
            val remoteMeta = UserScriptMetadata.parse(remoteMetaText)
            if (!UserScriptMetadata.isNewer(remoteMeta.version, meta.version)) {
                return UpdateResult.UpToDate
            }
            // The check URL may be metadata-only; fetch the full body from downloadURL.
            val downloadUrl = meta.downloadUrl.ifBlank { checkUrl }
            val newCode = if (downloadUrl == checkUrl && remoteMetaText.contains("==/UserScript==")) {
                remoteMetaText
            } else {
                HttpClientProvider.client.get(downloadUrl).bodyAsText()
            }
            scriptDao.update(script.copy(code = newCode, name = UserScriptMetadata.parse(newCode).name))
            reload()
            UpdateResult.Updated(meta.version, remoteMeta.version)
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "update failed")
        }
    }

    // --- backup / restore (BackupManager) ---

    /** Every installed script with its GM values, for the backup zip. */
    suspend fun getAllForBackup(): List<Pair<UserScript, Map<String, String>>> =
        scriptDao.getAll().map { it to valuesFor(it.id) }

    /**
     * Installs one script from a backup, append-only: a script whose `@name`
     * is already installed is left exactly as it is (code, enabled state and
     * GM values), so a restore can never downgrade or wipe a local script.
     * Returns the new id, or null when skipped/unparseable. Callers importing
     * a batch should [reload] once afterwards.
     */
    suspend fun importScript(
        code: String,
        enabled: Boolean,
        sourceUrl: String?,
        values: Map<String, String>,
    ): Long? {
        val meta = runCatching { UserScriptMetadata.parse(code) }.getOrNull() ?: return null
        if (meta.name.isBlank() || scriptDao.getAll().any { it.name == meta.name }) return null
        val id = runCatching { add(code, sourceUrl) }.getOrNull() ?: return null
        if (!enabled) scriptDao.getById(id)?.let { scriptDao.update(it.copy(enabled = false)) }
        values.forEach { (key, value) -> valueDao.setValue(UserScriptValue(id, key, value)) }
        return id
    }

    // --- GM_setValue / getValue storage (called from the JS bridge) ---

    suspend fun setValue(scriptId: Long, key: String, value: String) =
        valueDao.setValue(UserScriptValue(scriptId, key, value))

    suspend fun deleteValue(scriptId: Long, key: String) = valueDao.deleteValue(scriptId, key)

    private suspend fun valuesFor(scriptId: Long): Map<String, String> =
        valueDao.getForScript(scriptId).associate { it.key to it.value }

    /**
     * Builds `window.__einkbroInject([...])` for every enabled script, each
     * descriptor carrying its compiled match/exclude regexes, GM_info, a snapshot
     * of its stored values (so GM_getValue is synchronous in-page), and its body.
     * Returns null when nothing is enabled. The runtime shim does the actual
     * URL matching against `location.href`.
     */
    suspend fun buildInjectionJs(): String? {
        val enabled = scripts.filter { it.script.enabled }
        if (enabled.isEmpty()) return null
        val descriptors = enabled.map { parsed ->
            val m = parsed.metadata
            Descriptor(
                id = parsed.script.id,
                runAt = if (m.runAt == RunAt.DOCUMENT_START) "start" else "end",
                matches = UrlMatcher.matchRegexes(m.matches),
                includes = UrlMatcher.includeRegexes(m.includes),
                excludes = UrlMatcher.excludeRegexes(m.excludes),
                connects = m.connects,
                grants = m.grants,
                info = GmInfo(
                    GmInfoScript(
                        name = m.name, version = m.version, description = m.description,
                        matches = m.matches, includes = m.includes, excludes = m.excludes,
                        grant = m.grants, runAt = if (m.runAt == RunAt.DOCUMENT_START) "document-start" else "document-end",
                    )
                ),
                values = valuesFor(parsed.script.id),
                body = parsed.script.code,
            )
        }
        val arrayJson = json.encodeToString(ListSerializer(Descriptor.serializer()), descriptors)
        return "if (window.__einkbroInject) { window.__einkbroInject($arrayJson); }"
    }

    @Serializable
    private data class Descriptor(
        val id: Long,
        val runAt: String,
        val matches: List<String>,
        val includes: List<String>,
        val excludes: List<String>,
        val connects: List<String>,
        val grants: List<String>,
        val info: GmInfo,
        val values: Map<String, String>,
        val body: String,
    )

    @Serializable
    private data class GmInfo(
        val script: GmInfoScript,
        val scriptHandler: String = "EinkBro",
        val version: String = "1.0",
    )

    @Serializable
    private data class GmInfoScript(
        val name: String,
        val version: String,
        val description: String,
        val matches: List<String>,
        val includes: List<String>,
        val excludes: List<String>,
        val grant: List<String>,
        val runAt: String,
    )
}
