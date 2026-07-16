package info.plateaukao.einkbro.userscript

import info.plateaukao.einkbro.database.UserScript
import info.plateaukao.einkbro.util.System
import kotlinx.coroutines.delay

/** A userscript paired with its parsed metadata (metadata trimmed in the iOS stub). */
data class ParsedUserScript(
    val script: UserScript,
    var requiresContent: String = "",
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
 * In-memory port of the Android UserScriptManager (Room rows + on-disk script
 * bodies + OkHttp updates there). Holds sample scripts; add/update/delete
 * mutate the in-memory list, and update checks are simulated.
 */
class UserScriptManager {

    private var nextId = 4L

    private val store: MutableList<UserScript> = mutableListOf(
        UserScript(
            id = 1L,
            name = "Dark Reader Lite",
            enabled = true,
            code = sampleCode("Dark Reader Lite", "1.2.0"),
            sourceUrl = "https://greasyfork.org/scripts/dark-reader-lite.user.js",
            order = 0,
        ),
        UserScript(
            id = 2L,
            name = "Auto Skip Video Ads",
            enabled = true,
            code = sampleCode("Auto Skip Video Ads", "0.9.1"),
            sourceUrl = "https://greasyfork.org/scripts/auto-skip-video-ads.user.js",
            order = 1,
        ),
        UserScript(
            id = 3L,
            name = "",
            enabled = false,
            code = "console.log('no metadata block');",
            sourceUrl = null,
            order = 2,
        ),
    )

    var scripts: List<ParsedUserScript> = emptyList()
        private set

    init {
        rebuild()
    }

    suspend fun reload() {
        rebuild()
    }

    suspend fun add(code: String, sourceUrl: String? = null): Long {
        val id = nextId++
        store.add(
            UserScript(
                id = id,
                name = parseName(code),
                enabled = true,
                code = code,
                sourceUrl = sourceUrl,
                order = store.size,
            )
        )
        rebuild()
        return id
    }

    suspend fun update(script: UserScript) {
        val index = store.indexOfFirst { it.id == script.id }
        if (index >= 0) {
            store[index] = script.copy(name = parseName(script.code).ifBlank { script.name })
            rebuild()
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val index = store.indexOfFirst { it.id == id }
        if (index >= 0) {
            store[index] = store[index].copy(enabled = enabled)
            rebuild()
        }
    }

    suspend fun checkAndUpdate(id: Long): UpdateResult {
        val script = store.firstOrNull { it.id == id }
            ?: return UpdateResult.Failed("script not found")
        delay(600) // simulate the network round trip so the spinner is visible
        return if (script.sourceUrl.isNullOrBlank()) UpdateResult.NoSource
        else UpdateResult.UpToDate
    }

    suspend fun delete(id: Long) {
        store.removeAll { it.id == id }
        rebuild()
    }

    fun getById(id: Long): ParsedUserScript? = scripts.firstOrNull { it.script.id == id }

    private fun rebuild() {
        scripts = store.sortedBy { it.order }.map { ParsedUserScript(it) }
    }

    private fun parseName(code: String): String =
        Regex("""//\s*@name\s+(.+)""").find(code)?.groupValues?.get(1)?.trim().orEmpty()

    private fun sampleCode(name: String, version: String): String = buildString {
        append("// ==UserScript==\n")
        append("// @name $name\n")
        append("// @version $version\n")
        append("// @match https://*/*\n")
        append("// @run-at document-end\n")
        append("// ==/UserScript==\n")
        append("(function() {\n")
        append("  console.log('$name installed at ${System.currentTimeMillis()}');\n")
        append("})();\n")
    }
}
