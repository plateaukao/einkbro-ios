// Runs Readability over the current document and swaps in the reader body.
// Requires MozReadability.js + jsonld_article.js to have been evaluated first.
// Placeholders are filled by WebContentHelper.
(function() {
    __INLINE_CODE_STYLES__
    var scopedDoc = (typeof getReadabilityScopedDocument === 'function') ? getReadabilityScopedDocument() : null;
    var documentClone = scopedDoc || document.cloneNode(true);
    var article = new Readability(documentClone, __READABILITY_OPTIONS__).parse();
    document.innerHTMLCache = document.body.innerHTML;
    if (article) {
        article.readingTime = getReadingTime(article.length, document.documentElement.lang.substring(0, 2));
        document.body.outerHTML = createHtmlBody(article);
        disableSiteStyleSheets();
        var viewport = document.getElementsByName('viewport')[0];
        if (viewport) viewport.setAttribute('content', 'width=device-width');
    }
})();
