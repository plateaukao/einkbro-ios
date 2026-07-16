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
        // Extracted inline JS (reader/vertical/paging), filled via __PLACEHOLDER__.
        "vertical_scroll_helpers.js",
        "page_scroll.js",
        "replace_reader_body.js",
        "disable_reader_mode.js",
        "vertical_scroll_to.js",
        "scroll_to_top.js",
        "scroll_to_bottom.js",
        "engine_scroll_by_page.js",
        // Adblock content-rule list (WKContentRuleList JSON), compiled at startup.
        "adblock_rules.json",
        // Text selection + highlight (Phase 5).
        "selection_change.js",
        "highlight_selection.js",
        "link_longpress.js",
        // Services (Phase 6): in-place paragraph translation + page text.
        "android_interface_prelude.js",
        "translate_by_paragraph.js",
        "text_node_monitor.js",
        "clear_translation_elements.js",
        "get_raw_text.js",
        // Table of contents (Phase A): heading extraction + jump.
        "get_toc.js",
        "goto_toc.js",
        // Find on page (Phase E): highlight + step + clear.
        "find_onpage.js",
        // Split-screen scroll sync (Phase G): main pane reports scrollY.
        "split_scroll_report.js",
        // Userscripts (Phase H): GM_* runtime + per-page injection dispatcher.
        "userscript_runtime.js",
    )
}
