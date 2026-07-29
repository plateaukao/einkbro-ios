(function () {
    // body-is-the-scroller pages ignore window.scrollTo (see __einkbroDocScroller).
    var docScroller = window.__einkbroDocScroller && window.__einkbroDocScroller();
    if (docScroller) docScroller.scrollTo({top: 0, left: 0, behavior: 'instant'});
    else window.scrollTo({top: 0, left: 0, behavior: 'instant'});
})();
window.__einkbroScrollToTop && window.__einkbroScrollToTop();
