(function () {
    var nodes = Array.prototype.slice.call(
        document.querySelectorAll('h1, h2, h3, h4')
    ).filter(function (n) {
        return (n.innerText || '').trim().length > 0 && n.offsetParent !== null;
    });
    window.__ebTocNodes = nodes;
    return JSON.stringify(nodes.map(function (n) {
        return {
            level: parseInt(n.tagName.substring(1), 10),
            text: (n.innerText || '').trim().substring(0, 120)
        };
    }));
})();
