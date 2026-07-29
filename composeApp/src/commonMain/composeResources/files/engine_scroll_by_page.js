// Engine-level fallback paging: scroll by ~one viewport height.
// __SIGN__ is -1 for page up, 1 for page down.
(function () {
    var amount = __SIGN__ * window.innerHeight * 0.92;
    // body-is-the-scroller pages ignore window.scrollBy (see __einkbroDocScroller).
    var docScroller = window.__einkbroDocScroller && window.__einkbroDocScroller();
    if (docScroller) docScroller.scrollBy({top: amount, left: 0, behavior: 'instant'});
    else window.scrollBy({top: amount, left: 0, behavior: 'instant'});
})();
