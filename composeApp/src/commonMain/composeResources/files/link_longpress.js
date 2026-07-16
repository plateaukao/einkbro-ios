// Detects a long-press on an anchor and reports {url, text} to Kotlin via the
// einkbroLongPress message handler. iOS has no WebView.HitTestResult; a touch
// timer stands in. `-webkit-touch-callout:none` on links suppresses the native
// long-press action sheet so only our menu shows (text selection is untouched).
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
    function post(url, text) {
        try {
            window.webkit.messageHandlers.einkbroLongPress.postMessage(
                JSON.stringify({ url: url, text: text })
            );
        } catch (e) {}
    }
    function cancel() {
        if (timer) { clearTimeout(timer); timer = null; }
    }
    document.addEventListener("touchstart", function(e) {
        var link = findLink(e.target);
        if (!link) return;
        cancel();
        timer = setTimeout(function() {
            timer = null;
            post(link.href, (link.textContent || "").trim());
        }, 500);
    }, true);
    document.addEventListener("touchend", cancel, true);
    document.addEventListener("touchmove", cancel, true);
    document.addEventListener("touchcancel", cancel, true);
})();
