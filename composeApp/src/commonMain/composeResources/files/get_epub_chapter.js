// EPUB export (parity Phase I): capture the current page as one chapter.
// Runs Readability over a clone (non-destructive), rewrites each <img> to a
// local EPUB path, and serializes the article to well-formed XHTML via
// XMLSerializer (self-closes void elements so the EPUB stays valid XML).
// Requires MozReadability.js + jsonld_article.js to have been evaluated first.
// Returns JSON {title, xhtml, images:[{name,url}]} or {error}.
(function () {
    try {
        var article = null;
        try {
            var clone = document.cloneNode(true);
            article = new Readability(clone).parse();
        } catch (e) { article = null; }

        var title = (article && article.title) || document.title || 'Chapter';
        var container = document.createElement('div');
        container.innerHTML = article && article.content
            ? article.content
            : (document.body ? document.body.innerHTML : '');

        var imgs = container.querySelectorAll('img');
        var images = [];
        var idx = 0;
        for (var i = 0; i < imgs.length; i++) {
            var img = imgs[i];
            var src = img.currentSrc || img.getAttribute('src') || '';
            img.removeAttribute('srcset');
            img.removeAttribute('loading');
            if (!src || src.indexOf('data:') === 0) continue;
            // Resolve relative URLs against the document.
            try { src = new URL(src, document.baseURI).href; } catch (e) {}
            var clean = src.split('?')[0].split('#')[0];
            var m = clean.match(/\.(jpe?g|png|gif|webp|svg)$/i);
            var ext = m ? m[1].toLowerCase().replace('jpeg', 'jpg') : 'jpg';
            var name = 'images/img' + (idx++) + '.' + ext;
            images.push({ name: name, url: src });
            img.setAttribute('src', name);
        }

        var xhtml = new XMLSerializer().serializeToString(container);
        return JSON.stringify({ title: title, xhtml: xhtml, images: images });
    } catch (e) {
        return JSON.stringify({ error: String(e) });
    }
})();
