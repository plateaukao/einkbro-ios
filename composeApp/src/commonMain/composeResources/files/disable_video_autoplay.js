(function() {
    'use strict';
    if (window.__einkbroAutoplayBlocked) return;
    window.__einkbroAutoplayBlocked = true;

    // Feed sites (Instagram, Facebook, X, Threads) don't use the autoplay
    // attribute: they call video.play() from an IntersectionObserver when a
    // muted video scrolls into view, which also bypasses WebView's
    // mediaPlaybackRequiresUserGesture (muted playback is exempt). So playback
    // itself is intercepted here: play() only succeeds within a short window
    // after a real click/tap. Scrolling never fires click, so scroll-triggered
    // autoplay stays blocked while tapping a video or its play button works.
    var GESTURE_WINDOW_MS = 1500;
    var lastGestureTime = -GESTURE_WINDOW_MS;
    var approved = new WeakSet();

    function recentGesture() {
        return performance.now() - lastGestureTime < GESTURE_WINDOW_MS;
    }

    // A tap on a player's own chrome (YouTube's big play button, a thumbnail
    // overlay) approves that player's media element for good, not just for the
    // gesture window. Players that build their pipeline before calling play() —
    // YouTube fetches the player config and attaches MSE first — reach play()
    // long after GESTURE_WINDOW_MS is up, so window-only approval rejects the
    // one playback the user explicitly asked for and the player sits on its
    // spinner. The walk stops below body so a stray tap on the page background
    // can't approve every video on the page.
    function approveNearestMedia(target) {
        var node = target;
        while (node && node !== document.body && node !== document.documentElement) {
            if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') {
                approved.add(node);
                return;
            }
            var media = node.querySelector && node.querySelector('video, audio');
            if (media) {
                approved.add(media);
                return;
            }
            node = node.parentElement;
        }
    }

    ['click', 'dblclick'].forEach(function(type) {
        window.addEventListener(type, function(e) {
            if (!e.isTrusted) return;
            lastGestureTime = performance.now();
            approveNearestMedia(e.target);
        }, true);
    });
    window.addEventListener('keydown', function(e) {
        if (e.isTrusted && (e.key === ' ' || e.key === 'Enter' || e.key === 'k')) {
            lastGestureTime = performance.now();
        }
    }, true);

    var origPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function() {
        // Once user-approved, stay approved: sites re-call play() on rebuffer
        // and seek, and blocking those would stall a video mid-watch.
        if (approved.has(this) || recentGesture()) {
            approved.add(this);
            return origPlay.apply(this, arguments);
        }
        this.autoplay = false;
        this.removeAttribute('autoplay');
        // Reject the same way Chromium's autoplay policy does, so sites fall
        // into their handled path and show their own tap-to-play UI instead of
        // a stuck spinner.
        return Promise.reject(new DOMException(
            'play() can only be initiated by a user gesture (autoplay disabled in EinkBro).',
            'NotAllowedError'));
    };

    // Backstop for playback that never goes through the patched play():
    // Chromium starting a muted video with the autoplay attribute natively, or
    // a play() reference the page captured before this script ran (possible on
    // the evaluateJavascript fallback path for old WebViews).
    window.addEventListener('play', function(e) {
        var el = e.target;
        if (!el || typeof el.pause !== 'function') return;
        if (approved.has(el) || recentGesture()) {
            approved.add(el);
            return;
        }
        el.pause();
    }, true);

    // Strip autoplay attributes so most media never even attempts to start.
    function strip(el) {
        el.autoplay = false;
        el.removeAttribute('autoplay');
        if (!approved.has(el) && !el.paused) el.pause();
    }

    function stripAll(root) {
        if (!root.querySelectorAll) return;
        root.querySelectorAll('video, audio').forEach(strip);
    }

    new MutationObserver(function(mutations) {
        mutations.forEach(function(m) {
            m.addedNodes.forEach(function(node) {
                if (node.nodeType !== 1) return;
                if (node.tagName === 'VIDEO' || node.tagName === 'AUDIO') strip(node);
                stripAll(node);
            });
        });
    }).observe(document.documentElement || document, {childList: true, subtree: true});

    if (document.readyState !== 'loading') {
        stripAll(document);
    } else {
        document.addEventListener('DOMContentLoaded', function() {
            stripAll(document);
        });
    }
})();
