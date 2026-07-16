// Find-on-page for WKWebView (parity Phase E): wrap case-insensitive matches
// in <span class="eb-find-mark">, highlight the current one, scroll it into
// view. State lives on window.__ebFind so next/prev/clear persist across calls.
// Dispatched by command: cmd/arg placeholders are filled per invocation.
(function () {
    if (!window.__ebFind) {
        window.__ebFind = (function () {
            var marks = [];
            var index = 0;

            function highlight(i) {
                for (var k = 0; k < marks.length; k++) {
                    marks[k].style.backgroundColor = '#ffe000';
                    marks[k].style.color = '#000';
                }
                var m = marks[i];
                if (!m) return;
                m.style.backgroundColor = '#ff8c00';
                m.scrollIntoView({ block: 'center', inline: 'center' });
            }

            function clear() {
                var parents = [];
                for (var k = 0; k < marks.length; k++) {
                    var m = marks[k];
                    if (!m.parentNode) continue;
                    var t = document.createTextNode(m.textContent);
                    m.parentNode.replaceChild(t, m);
                    if (parents.indexOf(t.parentNode) < 0) parents.push(t.parentNode);
                }
                for (var p = 0; p < parents.length; p++) {
                    if (parents[p]) parents[p].normalize();
                }
                marks = [];
                index = 0;
                return { count: 0, index: 0 };
            }

            function find(query) {
                clear();
                if (!query) return { count: 0, index: 0 };
                var q = query.toLowerCase();
                var walker = document.createTreeWalker(
                    document.body, NodeFilter.SHOW_TEXT, {
                        acceptNode: function (node) {
                            if (!node.nodeValue || !node.nodeValue.trim()) {
                                return NodeFilter.FILTER_REJECT;
                            }
                            var p = node.parentNode;
                            if (!p) return NodeFilter.FILTER_REJECT;
                            var tag = p.nodeName.toLowerCase();
                            if (tag === 'script' || tag === 'style' ||
                                tag === 'noscript' || tag === 'textarea') {
                                return NodeFilter.FILTER_REJECT;
                            }
                            return NodeFilter.FILTER_ACCEPT;
                        }
                    }
                );
                var textNodes = [];
                var n;
                while ((n = walker.nextNode())) textNodes.push(n);
                for (var i = 0; i < textNodes.length; i++) {
                    var node = textNodes[i];
                    var text = node.nodeValue;
                    var lower = text.toLowerCase();
                    var at = lower.indexOf(q);
                    if (at < 0) continue;
                    var frag = document.createDocumentFragment();
                    var last = 0;
                    while (at >= 0) {
                        if (at > last) {
                            frag.appendChild(
                                document.createTextNode(text.slice(last, at))
                            );
                        }
                        var mark = document.createElement('span');
                        mark.className = 'eb-find-mark';
                        mark.style.backgroundColor = '#ffe000';
                        mark.style.color = '#000';
                        mark.textContent = text.slice(at, at + q.length);
                        frag.appendChild(mark);
                        marks.push(mark);
                        last = at + q.length;
                        at = lower.indexOf(q, last);
                    }
                    if (last < text.length) {
                        frag.appendChild(document.createTextNode(text.slice(last)));
                    }
                    node.parentNode.replaceChild(frag, node);
                }
                index = 0;
                if (marks.length) highlight(0);
                return { count: marks.length, index: marks.length ? 1 : 0 };
            }

            function step(delta) {
                if (!marks.length) return { count: 0, index: 0 };
                index = (index + delta + marks.length) % marks.length;
                highlight(index);
                return { count: marks.length, index: index + 1 };
            }

            return {
                find: find,
                next: function () { return step(1); },
                prev: function () { return step(-1); },
                clear: clear,
            };
        })();
    }

    var cmd = '__CMD__';
    var arg = '__ARG__';
    var r;
    if (cmd === 'find') r = window.__ebFind.find(decodeURIComponent(arg));
    else if (cmd === 'next') r = window.__ebFind.next();
    else if (cmd === 'prev') r = window.__ebFind.prev();
    else r = window.__ebFind.clear();
    return JSON.stringify(r || { count: 0, index: 0 });
})();
