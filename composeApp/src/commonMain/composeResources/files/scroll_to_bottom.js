(function () {
    // body-is-the-scroller pages ignore window.scrollTo (see __einkbroDocScroller).
    var docScroller = window.__einkbroDocScroller && window.__einkbroDocScroller();
    if (docScroller) {
        docScroller.scrollTo({top: docScroller.scrollHeight, left: 0, behavior: 'instant'});
    } else {
        window.scrollTo({
            top: document.documentElement.scrollHeight,
            left: 0,
            behavior: 'instant',
        });
    }
})();
