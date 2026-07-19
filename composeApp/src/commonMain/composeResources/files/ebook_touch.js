// Ebook touch-area mode: a plain tap on the left/right half of the screen
// turns the page (Android intercepts this natively in EBWebView; on iOS the
// page reports qualifying taps and Kotlin decides). The browser only emits a
// click for tap-like gestures, so scrolls and long presses pass through for
// free. Interactive elements and active text selections keep their normal
// behavior. Idempotent: re-evaluating only re-arms the enabled flag; the
// native side re-checks the pref on every message, so a stale-armed tab is
// harmless.
(function () {
    window.__einkbroEbookTouchEnabled = true;
    if (window.__einkbroEbookTouchInstalled) return;
    window.__einkbroEbookTouchInstalled = true;

    var INTERACTIVE = 'a, button, input, textarea, select, label, summary, ' +
        'video, audio, iframe, embed, [onclick], [role="button"], [contenteditable]';

    document.addEventListener('click', function (e) {
        if (!window.__einkbroEbookTouchEnabled) return;
        if (!e.isTrusted) return;
        var t = e.target;
        if (t && t.closest && t.closest(INTERACTIVE)) return;
        var sel = window.getSelection && window.getSelection();
        if (sel && sel.type === 'Range') return;
        e.preventDefault();
        e.stopPropagation();
        var side = (e.clientX < window.innerWidth / 2) ? 'left' : 'right';
        try { window.webkit.messageHandlers.einkbroEbookTap.postMessage(side); } catch (err) {}
    }, true);
})();
