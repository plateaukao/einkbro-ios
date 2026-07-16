/*
 * Batch image collector (parity Phase M), ported from Android's
 * get_remaining_images.js. Starting from the long-pressed image, collects every
 * later <img>'s src (unlazying data-src / removing loading=lazy) that looks like
 * a jpg/png, and returns them as a JSON array for "translate all images".
 * Host replaces %%IMAGE_URL%%.
 */
(function () {
    var currentUrl = '%%IMAGE_URL%%';
    var imgs = document.querySelectorAll('img');
    var urls = [];
    var found = false;
    for (var i = 0; i < imgs.length; i++) {
        if (imgs[i].src === currentUrl) {
            found = true;
        }
        if (found) {
            try {
                imgs[i].removeAttribute('loading');
                if (imgs[i].dataset && imgs[i].dataset.src) {
                    imgs[i].src = imgs[i].dataset.src;
                }
            } catch (e) {}
            var src = imgs[i].src;
            if (src && src.startsWith('http') &&
                (src.toLowerCase().includes('jpg') || src.toLowerCase().includes('png'))) {
                urls.push(src);
            }
        }
    }
    return JSON.stringify(urls);
})();
