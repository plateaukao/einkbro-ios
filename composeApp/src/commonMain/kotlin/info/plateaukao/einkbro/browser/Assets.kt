package info.plateaukao.einkbro.browser

import info.plateaukao.einkbro.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The Android app's JS/CSS assets, shipped as compose resources and preloaded
 * once at startup ([preload] runs before the first tab is created).
 */
object Assets {
    private val cache = mutableMapOf<String, String>()

    val isLoaded: Boolean get() = cache.isNotEmpty()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun preload() {
        if (isLoaded) return
        NAMES.forEach { name ->
            cache[name] = Res.readBytes("files/$name").decodeToString()
        }
    }

    fun get(name: String): String = cache[name]
        ?: error("asset $name not preloaded — call Assets.preload() first")

    private val NAMES = listOf(
        "MozReadability.js",
        "jsonld_article.js",
        "readerview.css",
        "verticalReaderview.css",
        "vertical_layout.css",
        "process_text_nodes.js",
        "measure_line_advance.js",
        "fix_scrolling.js",
        "update_css_slot.js",
        "set_viewport_content.js",
        "disable_video_autoplay.js",
        "audio_only_mode.js",
        "audio_only_mode_off.js",
        "highlight.css",
    )
}
