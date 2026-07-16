// Wraps the current selection in div.__HIGHLIGHT_CLASS__ elements (styled by
// highlight.css). Uses the classic "safe ranges" split so surroundContents
// never throws across element boundaries. Click a highlight to unwrap it.
// Port of Android's text_selection_highlight.js (%s -> __HIGHLIGHT_CLASS__).
(function() {
    function highlightRange(range) {
        var newNode = document.createElement("div");
        newNode.className = "__HIGHLIGHT_CLASS__";
        range.surroundContents(newNode);
        newNode.onclick = function() {
            newNode.outerHTML = newNode.innerHTML;
        };
    }

    function getSafeRanges(dangerous) {
        var a = dangerous.commonAncestorContainer;
        var s = new Array(0), rs = new Array(0);
        if (dangerous.startContainer != a) {
            for (var i = dangerous.startContainer; i != a; i = i.parentNode) { s.push(i); }
        }
        if (s.length > 0) {
            for (var i = 0; i < s.length; i++) {
                var xs = document.createRange();
                if (i) {
                    xs.setStartAfter(s[i - 1]);
                    xs.setEndAfter(s[i].lastChild);
                } else {
                    xs.setStart(s[i], dangerous.startOffset);
                    xs.setEndAfter((s[i].nodeType == Node.TEXT_NODE) ? s[i] : s[i].lastChild);
                }
                rs.push(xs);
            }
        }
        var e = new Array(0), re = new Array(0);
        if (dangerous.endContainer != a) {
            for (var i = dangerous.endContainer; i != a; i = i.parentNode) { e.push(i); }
        }
        if (e.length > 0) {
            for (var i = 0; i < e.length; i++) {
                var xe = document.createRange();
                if (i) {
                    xe.setStartBefore(e[i].firstChild);
                    xe.setEndBefore(e[i - 1]);
                } else {
                    xe.setStartBefore((e[i].nodeType == Node.TEXT_NODE) ? e[i] : e[i].firstChild);
                    xe.setEnd(e[i], dangerous.endOffset);
                }
                re.unshift(xe);
            }
        }
        if ((s.length > 0) && (e.length > 0)) {
            var xm = document.createRange();
            xm.setStartAfter(s[s.length - 1]);
            xm.setEndBefore(e[e.length - 1]);
        } else {
            return [dangerous];
        }
        rs.push(xm);
        return rs.concat(re);
    }

    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    var ranges = getSafeRanges(sel.getRangeAt(0));
    for (var i = 0; i < ranges.length; i++) {
        try { highlightRange(ranges[i]); } catch (e) {}
    }
    sel.removeAllRanges();
})();
