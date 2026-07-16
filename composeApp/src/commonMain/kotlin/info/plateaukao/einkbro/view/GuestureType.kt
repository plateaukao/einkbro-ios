package info.plateaukao.einkbro.view

import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import org.jetbrains.compose.resources.StringResource

enum class GestureType(val value: String, val resId: StringResource) {
    NothingHappen("01", Res.string.nothing),
    Forward("02", Res.string.forward_in_history),
    Backward("03", Res.string.back_in_history),
    ScrollToTop("04", Res.string.scroll_to_top),
    ScrollToBottom("05", Res.string.scroll_to_bottom),
    ToLeftTab("06", Res.string.switch_to_left_tab),
    ToRightTab("07", Res.string.switch_to_right_tab),
    Overview("08", Res.string.show_overview),
    OpenNewTab("09", Res.string.open_new_tab),
    CloseTab("10", Res.string.close_tab),
    PageUp("11", Res.string.page_up),
    PageDown("12", Res.string.page_down),
    Bookmark("13", Res.string.bookmarks),
    Back("14", Res.string.back),
    Fullscreen("15", Res.string.fullscreen),
    Refresh("16", Res.string.refresh),
    Menu("17", Res.string.menu),
    TouchPagination("18", Res.string.toggle_touch_turn_page),
    KeyPageUp("19", Res.string.key_page_up),
    KeyPageDown("20", Res.string.key_page_down),
    KeyLeft("21", Res.string.key_left),
    KeyRight("22", Res.string.key_right),
    InputUrl("23", Res.string.input_url),
    ;

    companion object {
        fun from(value: String): GestureType =
            entries.firstOrNull { it.value == value } ?: NothingHappen
    }
}
