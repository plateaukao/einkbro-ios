// Rewrites the viewport meta to permit pinch-zoom (removes user-scalable=no /
// maximum-scale caps) while preserving the page's width. Mirrors Android's
// WebContentPostProcessor enableZoomJs; WKWebView otherwise obeys the page's
// viewport and pinch-to-zoom does nothing.
(function () {
    var vp = document.querySelector('meta[name=viewport]');
    if (!vp) {
        vp = document.createElement('meta');
        vp.setAttribute('name', 'viewport');
        vp.setAttribute('content', 'width=device-width');
        (document.head || document.documentElement).appendChild(vp);
    }
    var c = vp.getAttribute('content') || 'width=device-width';
    c = c.replace(/,?\s*user-scalable\s*=\s*(no|0)/gi, '')
         .replace(/,?\s*maximum-scale\s*=\s*[0-9.]+/gi, '')
         .replace(/,?\s*minimum-scale\s*=\s*[0-9.]+/gi, '');
    if (!/maximum-scale/i.test(c)) c += ', maximum-scale=10.0';
    // Pin the floor at 1.0. Without an explicit minimum-scale, WKWebView picks
    // its own min that fits the page's full layout width — for sites wider than
    // device-width (e.g. facebook.com) that lands below 1.0, letting the user
    // zoom out and pan the shrunken page. Locking it to 1.0 permits zoom-in
    // (up to maximum-scale) while forbidding zoom-out past the fit-to-width base.
    c += ', minimum-scale=1.0, user-scalable=yes';
    vp.setAttribute('content', c);
})();
