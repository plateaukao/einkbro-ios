// Reports the current text selection (text + start/end rects, CSS px) to Kotlin
// via the einkbroSelection message handler; posts an empty text when cleared.
// Port of Android's text_selection_change.js (androidApp.getAnchorPosition).
(function() {
    if (window.__ebSelectionListener) return;
    window.__ebSelectionListener = true;
    var lastText = "";
    function caretRect(node, offset) {
        var r = document.createRange();
        r.setStart(node, offset);
        r.setEnd(node, offset);
        return r.getBoundingClientRect();
    }
    function post(payload) {
        try { window.webkit.messageHandlers.einkbroSelection.postMessage(JSON.stringify(payload)); }
        catch (e) {}
    }
    function report() {
        var sel = window.getSelection();
        var text = sel ? sel.toString() : "";
        if (text === lastText) return;
        lastText = text;
        if (!sel || sel.rangeCount === 0 || text.length === 0) {
            post({ text: "" });
            return;
        }
        var range = sel.getRangeAt(0);
        var start = caretRect(range.startContainer, range.startOffset);
        var end = caretRect(range.endContainer, range.endOffset);
        post({
            text: text,
            left: start.left, top: start.top, right: end.right, bottom: end.bottom
        });
    }
    document.addEventListener("selectionchange", report);
})();
