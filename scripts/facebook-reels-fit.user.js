// ==UserScript==
// @name         Facebook Reel Fit
// @namespace    einkbro
// @version      1.0
// @description  Enlarge small desktop Facebook Reels without changing the site's view mode.
// @match        https://www.facebook.com/*
// @match        https://m.facebook.com/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(() => {
    let scaled = null;

    function restore() {
        if (!scaled) return;
        scaled.box.style.transform = scaled.transform;
        scaled.box.style.transformOrigin = scaled.origin;
        scaled = null;
    }

    function update() {
        if (!/^\/reel(?:\/|$)/.test(location.pathname)) {
            restore();
            return;
        }

        const videos = [...document.querySelectorAll('video')].filter(video => {
            const rect = video.getBoundingClientRect();
            return rect.width && rect.top < innerHeight && rect.bottom > 0;
        });
        const video = videos.find(video => !video.paused) || videos[0];
        const width = video?.offsetWidth || 0;
        if (!width || width >= innerWidth * 0.75) {
            restore(); // The mobile Reel already fills the screen.
            return;
        }

        // The smallest video wrapper whose parent is wider also carries
        // Facebook's player overlays. Its generated class names can change.
        let box = video.parentElement;
        while (box.parentElement && box.parentElement.offsetWidth <= width * 1.25) {
            box = box.parentElement;
        }

        const key = `${innerWidth}/${innerHeight}/${width}/${video.offsetHeight}`;
        if (scaled?.box === box && scaled.key === key &&
            box.style.transform === scaled.applied) return;
        restore();

        const rect = video.getBoundingClientRect();
        const scale = Math.min(2, (innerWidth - 125) / width,
            (innerHeight - 80) / video.offsetHeight);
        if (scale <= 1) return;

        const left = rect.left - (width * scale - width) / 2;
        const applied = `translateX(${4 - left}px) scale(${scale})`;
        scaled = {
            box, key, applied,
            transform: box.style.transform,
            origin: box.style.transformOrigin,
        };
        box.style.transformOrigin = 'center center';
        box.style.transform = applied;
    }

    update();
    setInterval(update, 700); // Facebook changes Reels without a page load.
})();
