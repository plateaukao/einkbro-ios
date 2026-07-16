// Split-screen scroll sync (parity Phase G): the main pane posts its vertical
// scroll position so the host can mirror it into the second pane (one-way).
(function () {
    if (window.__ebSplitScrollBound) return;
    window.__ebSplitScrollBound = true;
    var pending = false;
    window.addEventListener('scroll', function () {
        if (pending) return;
        pending = true;
        requestAnimationFrame(function () {
            pending = false;
            try {
                window.webkit.messageHandlers.einkbroSplitScroll.postMessage(String(window.scrollY));
            } catch (e) {}
        });
    }, { passive: true });
})();
