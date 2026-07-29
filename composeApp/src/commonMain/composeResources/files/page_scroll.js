// One entry point for all three paging modes; matches Android's
// WebViewNavigationHelper math, in CSS px. Placeholders are filled by
// WebContentHelper before evaluation.
(function(dir) {
    if (__IS_VERTICAL_READER__) {
        __VERTICAL_SCROLL_HELPERS__
        var line = __LINE_ADVANCE__;
        var usable = window.innerWidth - 40;
        var step = (line > 1 && line < usable) ? Math.floor(usable / line) * line : usable;
        // dir=+1 (pageDown) advances toward the document end (leftward);
        // __ebSetFromStart hides the vertical-rl scrollLeft sign convention.
        var cur = Math.round(__ebFromStart() / step);
        __ebSetFromStart(Math.min(Math.max((cur + dir) * step, 0), __ebMax()));
        return;
    }
    if (__TWO_COLUMN__ && matchMedia('(orientation: landscape)').matches) {
        var w = window.innerWidth;
        var maxX = Math.max(0, document.documentElement.scrollWidth - w);
        var page = Math.round(window.scrollX / w) + dir;
        window.scrollTo({left: Math.min(Math.max(page * w, 0), maxX), top: 0, behavior: 'instant'});
        return;
    }
    // __einkbroPageScroll returns the STRING "true"/"false" (its Android bridge
    // contract). Only "true" means an inner scrollable handled the scroll; any
    // other value must fall through to the document-level scroll below.
    if (window.__einkbroPageScroll &&
        window.__einkbroPageScroll(dir, __RESERVE_PCT__, __RESERVE_PX__) === "true") return;
    var usableH = window.innerHeight * (1 - __RESERVE_PCT__) - __RESERVE_PX__;
    // Sites that make body the scroller (see __einkbroDocScroller) ignore
    // window.scrollBy entirely, so drive that element instead.
    var docScroller = window.__einkbroDocScroller && window.__einkbroDocScroller();
    if (docScroller) {
        docScroller.scrollBy({top: dir * usableH, left: 0, behavior: 'instant'});
        return;
    }
    window.scrollBy({top: dir * usableH, left: 0, behavior: 'instant'});
})(__DIRECTION__);
