// Detects a long-press on an anchor and reports {url, text, x, y} to Kotlin via
// the einkbroLongPress message handler. iOS has no WebView.HitTestResult; a touch
// timer stands in. `-webkit-touch-callout:none` on links suppresses the native
// long-press action sheet so only our menu shows (text selection is untouched).
// x/y are the touch point in viewport CSS px — the same space
// selection_change.js reports its rects in; Kotlin anchors the context menu there.
(function() {
    if (window.__ebLongPressListener) return;
    window.__ebLongPressListener = true;

    var style = document.createElement("style");
    style.textContent = "a { -webkit-touch-callout: none !important; }";
    (document.head || document.documentElement).appendChild(style);

    var timer = null;
    function findLink(el) {
        while (el && el !== document.body) {
            if (el.tagName && el.tagName.toLowerCase() === "a" && el.href) return el;
            el = el.parentNode;
        }
        return null;
    }
    function post(url, text, x, y) {
        try {
            window.webkit.messageHandlers.einkbroLongPress.postMessage(
                JSON.stringify({ url: url, text: text, x: x, y: y })
            );
        } catch (e) {}
    }
    function cancel() {
        if (timer) { clearTimeout(timer); timer = null; }
    }
    document.addEventListener("touchstart", function(e) {
        var link = findLink(e.target);
        if (!link) return;
        // Read the coordinates now: the touch list is empty by the time the
        // timer fires.
        var touch = e.touches && e.touches[0];
        var x = touch ? touch.clientX : 0;
        var y = touch ? touch.clientY : 0;
        cancel();
        timer = setTimeout(function() {
            timer = null;
            post(link.href, (link.textContent || "").trim(), x, y);
        }, 500);
    }, true);
    document.addEventListener("touchend", cancel, true);
    document.addEventListener("touchmove", cancel, true);
    document.addEventListener("touchcancel", cancel, true);
})();
