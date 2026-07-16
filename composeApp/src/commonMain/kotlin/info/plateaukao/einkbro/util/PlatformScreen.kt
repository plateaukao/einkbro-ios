package info.plateaukao.einkbro.util

/**
 * Screen geometry the ported UI branches on for tablet/wide two-column layouts
 * (bookmarks grid, settings columns, URL autocomplete). Android read this off the
 * Configuration; iOS reads UIScreen.
 */
expect object PlatformScreen {
    /** True on iPad-class devices (short side >= 600pt) — enables two-column layouts. */
    fun isWideLayout(): Boolean

    fun isLandscape(): Boolean
}
