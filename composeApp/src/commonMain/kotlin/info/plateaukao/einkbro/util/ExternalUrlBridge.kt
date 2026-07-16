package info.plateaukao.einkbro.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Delivers URLs opened from outside the app — the custom `einkbro://` scheme,
 * http(s) hand-offs, and `.webarchive` file opens — into the running Compose UI.
 *
 * The platform entry point (iOS SwiftUI `onOpenURL`) calls [submit]; BrowserScreen
 * collects [urls] and opens each in a tab. replay = 1 so a URL that arrives during
 * cold launch, before BrowserScreen starts collecting, is still delivered.
 */
object ExternalUrlBridge {
    private val _urls = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val urls: SharedFlow<String> = _urls.asSharedFlow()

    fun submit(raw: String) {
        val target = normalize(raw) ?: return
        _urls.tryEmit(target)
    }

    /**
     * Unwraps the target from an incoming URL:
     *  - `einkbro://open?url=<percent-encoded>` → the decoded target
     *  - `einkbro://<rest>` → `rest` (adding https:// if it has no scheme)
     *  - anything else (http/https/file) → returned as-is
     * Returns null for an empty/garbage einkbro:// URL.
     */
    fun normalize(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("einkbro://", ignoreCase = true)) return url

        val rest = url.substring("einkbro://".length)
        if (rest.isEmpty()) return null

        // einkbro://open?url=<enc>
        val openPrefix = "open?url="
        val idx = rest.indexOf(openPrefix)
        if (rest.startsWith("open", ignoreCase = true) && idx >= 0) {
            val enc = rest.substring(idx + openPrefix.length)
            val decoded = percentDecode(enc).trim()
            return decoded.ifBlank { null }
        }

        // einkbro://<rest> — rest is the bare target.
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(rest)
        return if (hasScheme) rest else "https://$rest"
    }

    private fun percentDecode(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length -> {
                    val hex = s.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar()); i += 3
                    } else {
                        out.append(c); i++
                    }
                }
                c == '+' -> { out.append(' '); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
