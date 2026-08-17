// Fully tears down by-paragraph / in-place translation and restores the
// original DOM. Disconnecting the observers is mandatory: translate_by_paragraph.js
// installs a MutationObserver and text_node_monitor.js an IntersectionObserver, both
// backed by a text cache. If we only restore innerHTML, those observers fire on the
// freshly-restored nodes and re-apply the cached translations, so the clear appears
// to do nothing.
(function() {
    // 1. Stop the MutationObserver that re-marks content as it renders.
    if (window._translateMutationObserver) {
        try { window._translateMutationObserver.disconnect(); } catch (e) {}
        window._translateMutationObserver = null;
    }
    // 2. Stop the IntersectionObserver that requests/applies translations on scroll.
    if (window._translateObserver) {
        try { window._translateObserver.disconnect(); } catch (e) {}
        window._translateObserver = null;
    }
    // 3. Drop the rebind hook and the node-tracking sets so a later translate run
    //    starts from a clean slate instead of skipping "already observed" nodes.
    window._translateRebindObserver = null;
    window._translateObservedNodes = null;
    window._translateRequested = null;
    // Unlike the two WeakSets above, the retry queue is a plain Set, so leaving it
    // populated would hold strong references to the elements this reset detaches.
    window._translateRetryQueue = null;
    // 4. Clear the text cache so restored nodes aren't instantly re-translated.
    if (window._translateTextCache) {
        try { window._translateTextCache.clear(); } catch (e) {}
    }

    // 5. Restore the pre-translation DOM and clear the flags.
    if (document.originalInnerHTML) {
        document.body.innerHTML = document.originalInnerHTML;
        delete document.originalInnerHTML;
        delete document.translatedInnerHTML;
    }
    document.body.classList.remove("translated");
    document.body.classList.remove("translated_but_hide");
    window._translateInPlace = false;
})();
