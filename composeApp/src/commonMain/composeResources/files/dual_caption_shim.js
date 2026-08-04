/*
 * Dual YouTube captions (parity Phase M), overlay implementation.
 *
 * Android merges the second language into the caption JSON by answering the
 * player's own `timedtext` request in `shouldInterceptRequest`
 * (NinjaWebViewClient -> DualCaptionProcessor): one request in, one response
 * out. WKWebView has no equivalent for https subresources — WKURLSchemeHandler
 * refuses schemes WebKit handles natively, NSURLProtocol doesn't reach the
 * networking process, and WKContentRuleList can block but not rewrite.
 *
 * An earlier port emulated the interception by patching window.fetch and
 * XMLHttpRequest to rewrite the response in-page. That is where every caption
 * bug came from: the two hooks re-entered each other and turned one caption
 * load into four requests (YouTube answered with 429s, and the HTML error page
 * reached the player as caption JSON), and the fabricated XHR completion
 * sequence let consumers process the same body more than once, duplicating
 * every line after an SPA navigation.
 *
 * So this no longer touches the player's request path at all. The player loads
 * its own captions untouched; the host fetches the translated track natively
 * (YouTubeCaptionFetcher, via the InnerTube ANDROID client) and hands it here,
 * and we draw the second line ourselves under the player's caption window.
 * Drawing it also means we own the spacing, instead of inheriting the wide gap
 * YouTube puts between two `.caption-visual-line` blocks.
 *
 * Host replaces %%DUAL_CAPTION_LOCALE%% (empty string disables the feature).
 */
(function () {
  var LOCALE = '%%DUAL_CAPTION_LOCALE%%';
  if (!LOCALE) return;
  if (window.__einkbroDualCaptionInstalled) return;
  window.__einkbroDualCaptionInstalled = true;

  var OVERLAY_ID = 'einkbro-dual-caption';
  // Longest gap between two cues we treat as "still speaking" rather than silence.
  var BRIDGE_MS = 5000;
  var cues = [];           // [{ start, end, text }] in ms — translated, for the overlay
  var nativeCues = [];     // same shape, "original\ntranslated" — for AVKit fullscreen
  var cuesVideoId = null;  // video the cues belong to
  var startedVideoId = null;
  var pageAttempts = 0;    // in-page track lookups tried for this video
  var askedHost = false;   // native fallback already requested for this video
  var pageState = 'idle';  // idle | inflight | done | failed
  // The player publishes its track list only once it has a player response;
  // on a cold load and after an SPA nav that takes a moment.
  var MAX_PAGE_ATTEMPTS = 40;

  function currentVideoId() {
    var m = /[?&]v=([A-Za-z0-9_-]+)/.exec(location.href);
    return m ? m[1] : null;
  }

  function playerEl() {
    return document.querySelector('#movie_player') ||
      document.querySelector('.html5-video-player');
  }

  /**
   * The caption tracks the *player* knows about. Taken from the live player
   * response rather than ytInitialPlayerResponse, which goes stale across SPA
   * navigation.
   */
  function playerTracks() {
    var response = null;
    var player = playerEl();
    if (player && typeof player.getPlayerResponse === 'function') {
      try { response = player.getPlayerResponse(); } catch (e) { /* not ready */ }
    }
    if (!response) response = window.ytInitialPlayerResponse;
    var list = response && response.captions &&
      response.captions.playerCaptionsTracklistRenderer;
    return (list && list.captionTracks) || [];
  }

  /**
   * The track the player is actually rendering.
   *
   * This is the whole point of reading the list from the page: sourcing cues
   * from a different track than the one on screen (the host's InnerTube lookup
   * prefers a manual track, the player may well be showing ASR) gives a
   * different segmentation, so our line drifts against the caption it is meant
   * to sit under and mostly fails to match at all.
   */
  function activeTrack(tracks) {
    if (!tracks.length) return null;
    var player = playerEl();
    if (player && typeof player.getOption === 'function') {
      var current = null;
      try { current = player.getOption('captions', 'track'); } catch (e) { /* captions off */ }
      if (current && current.languageCode) {
        var exact = tracks.filter(function (t) {
          return t.languageCode === current.languageCode &&
            (t.kind || '') === (current.kind || '');
        })[0];
        if (exact) return exact;
        var byLanguage = tracks.filter(function (t) {
          return t.languageCode === current.languageCode;
        })[0];
        if (byLanguage) return byLanguage;
      }
    }
    return tracks[0];
  }

  /** That track, as json3, machine-translated into our locale. */
  function translatedTrackUrl(track) {
    var url = track.baseUrl;
    url = (url.indexOf('fmt=') === -1)
      ? url + '&fmt=json3'
      : url.replace(/fmt=[^&]*/, 'fmt=json3');
    return /[?&]tlang=/.test(url)
      ? url.replace(/([?&])tlang=[^&]*/, '$1tlang=' + LOCALE)
      : url + '&tlang=' + LOCALE;
  }

  /** That track, as json3, untranslated — the first line of the fullscreen pair. */
  function originalTrackUrl(track) {
    var url = track.baseUrl;
    url = (url.indexOf('fmt=') === -1)
      ? url + '&fmt=json3'
      : url.replace(/fmt=[^&]*/, 'fmt=json3');
    return url.replace(/([?&])tlang=[^&]*&?/, '$1').replace(/[?&]$/, '');
  }

  /**
   * Pair each original cue with its translation for the native track. tlang
   * preserves the source track's event timings, so start-time equality is the
   * join key (the same key Android's DualCaptionProcessor merges on).
   */
  function mergeNativeCues(origCues, transCues) {
    var byStart = {};
    transCues.forEach(function (c) { byStart[c.start] = c.text; });
    return origCues.map(function (c) {
      var t = byStart[c.start];
      return { start: c.start, end: c.end, text: t ? c.text + '\n' + t : c.text };
    });
  }

  /** json3 timedtext -> cue list. */
  function parseCues(jsonText) {
    var out = [];
    var data;
    try {
      data = JSON.parse(jsonText);
    } catch (e) {
      return out;
    }
    (data.events || []).forEach(function (event) {
      if (!event.segs || !event.segs.length) return;
      var text = event.segs.map(function (s) { return s.utf8 || ''; }).join('').trim();
      if (!text) return;
      var start = event.tStartMs || 0;
      // A missing duration means "until the next cue"; 4s is YouTube's own
      // fallback and only ever applies to the last event.
      var dur = event.dDurationMs || 4000;
      out.push({ start: start, end: start + dur, text: text });
    });
    out.sort(function (a, b) { return a.start - b.start; });
    for (var i = 0; i < out.length - 1; i++) {
      if (out[i].end > out[i + 1].start) {
        // Overlap: clamp, so exactly one cue is current at any time.
        out[i].end = out[i + 1].start;
      } else if (out[i + 1].start - out[i].end < BRIDGE_MS) {
        // Gap: extend to the next cue. Auto-generated tracks leave small holes
        // between utterances, and the player holds its own caption on screen
        // across them — without bridging, our line blinks out for a beat every
        // few seconds and the caption looks single-language much of the time.
        // Gaps longer than BRIDGE_MS are real silence and stay uncovered, so a
        // stale line never sits under a new caption.
        out[i].end = out[i + 1].start;
      }
    }
    return out;
  }

  // Host -> page. Called by the native side once it has the caption tracks.
  window.__einkbroDualCaption = {
    setCues: function (videoId, jsonText, originalJsonText) {
      // A reply that landed after the user moved on is not ours to show.
      if (videoId !== currentVideoId()) return;
      cues = parseCues(jsonText);
      var original = originalJsonText ? parseCues(originalJsonText) : [];
      // Without the untranslated copy, fullscreen degrades to the
      // translation alone.
      nativeCues = original.length ? mergeNativeCues(original, cues) : cues;
      cuesVideoId = videoId;
    },
  };

  function requestCues(videoId) {
    try {
      window.webkit.messageHandlers.einkbroDualCaption.postMessage(videoId);
    } catch (e) { /* handler absent (non-host page); overlay stays off */ }
  }

  /**
   * Primary cue source: fetch the translated copy of the player's own track
   * from inside the page (same origin and cookies as the player's caption
   * request, so it is served wherever the player's is). Returns false while
   * the player hasn't published its track list yet — tick() retries — and true
   * once the fetch is under way, after which it owns pageState: inflight, then
   * done (cues installed) or failed (tick hands over to the host fallback).
   */
  function loadFromPage(videoId) {
    var track = activeTrack(playerTracks());
    if (!track || !track.baseUrl) return false;
    pageState = 'inflight';
    var textOf = function (resp) {
      if (!resp.ok) throw new Error('timedtext ' + resp.status);
      return resp.text();
    };
    Promise.all([
      fetch(translatedTrackUrl(track)).then(textOf),
      // The untranslated copy, only needed for the fullscreen pair — losing
      // it degrades fullscreen to translated-only rather than killing the
      // feature, hence the null instead of a rejection.
      fetch(originalTrackUrl(track)).then(textOf).catch(function () { return null; }),
    ])
      .then(function (parts) {
        // The user may have moved on while the request was in flight; the
        // result (and any state transition) belongs to the old video then.
        if (videoId !== currentVideoId()) return;
        var translated = parseCues(parts[0]);
        if (!translated.length) {
          // Fetched fine but nothing in it: an empty translated track. The
          // host's InnerTube lookup may pick a different (manual) track that
          // does translate, so treat this as a failure and let it try.
          pageState = 'failed';
          return;
        }
        var original = parts[1] ? parseCues(parts[1]) : [];
        cues = translated;
        nativeCues = original.length
          ? mergeNativeCues(original, translated)
          : translated;
        cuesVideoId = videoId;
        pageState = 'done';
      })
      .catch(function () {
        if (videoId !== currentVideoId()) return;
        pageState = 'failed';
      });
    return true;
  }

  function cueAt(ms) {
    for (var i = 0; i < cues.length; i++) {
      if (ms >= cues[i].start && ms < cues[i].end) return cues[i];
    }
    return null;
  }

  function removeOverlay() {
    var el = document.getElementById(OVERLAY_ID);
    if (el && el.parentNode) el.parentNode.removeChild(el);
  }

  /**
   * Fullscreen on iPhone is AVKit's native player (m.youtube.com calls
   * webkitEnterFullscreen regardless of the Fullscreen API being available),
   * and AVKit never paints the page — no DOM overlay, and none of YouTube's
   * own DOM captions either. The one thing that can draw text there is the
   * native caption renderer, which WebKit feeds from the video's WebVTT text
   * tracks. So the cues ride a real text track: shown in fullscreen, disabled
   * in-page (where the overlay draws instead, and where WebKit would
   * otherwise paint ::cue boxes on top of YouTube's captions).
   *
   * Cues are (re)filled lazily on first show per video, so the track costs
   * nothing until fullscreen is actually used.
   */
  function syncNativeTrack(video, wantShowing) {
    var Cue = window.VTTCue || window.TextTrackCue;
    if (!Cue || typeof video.addTextTrack !== 'function') return;
    var track = video.__einkbroTrack;
    if (!track) {
      if (!wantShowing) return;
      track = video.addTextTrack('subtitles', 'EinkBro dual caption', LOCALE);
      video.__einkbroTrack = track;
    }
    if (wantShowing && track.__cuesFor !== cuesVideoId) {
      while (track.cues && track.cues.length) track.removeCue(track.cues[0]);
      nativeCues.forEach(function (c) {
        try { track.addCue(new Cue(c.start / 1000, c.end / 1000, c.text)); } catch (e) { /* bad cue */ }
      });
      track.__cuesFor = cuesVideoId;
    }
    var mode = wantShowing ? 'showing' : 'disabled';
    // Re-asserted every tick while fullscreen: YouTube's player manages the
    // track list too and may flip modes under us.
    if (track.mode !== mode) track.mode = mode;
  }

  /**
   * The player rebuilds `.caption-window` as cues change, so the overlay is
   * re-parented every tick rather than kept as a long-lived child. Sitting
   * inside the caption window means it inherits the window's position and moves
   * with it (including audio-only mode's re-centering).
   */
  function render(text, segment) {
    // Attach to the window *container*, not to `.caption-window` or
    // `.captions-text`. Those are rebuilt on every caption update — continuously
    // for rolling auto-generated captions — so a node parented inside them is
    // wiped within milliseconds and only flickers back on the next tick. The
    // container is stable, so we sit in it and position ourselves against the
    // caption window's box each frame.
    var win = segment.closest('.caption-window');
    var container = win && win.parentNode;
    if (!win || !container) { removeOverlay(); return; }

    var el = document.getElementById(OVERLAY_ID);
    if (!el) {
      el = document.createElement('div');
      el.id = OVERLAY_ID;
      el.appendChild(document.createElement('span'));
    }
    if (el.parentNode !== container) container.appendChild(el);

    var containerBox = container.getBoundingClientRect();
    var windowBox = win.getBoundingClientRect();
    var cs = window.getComputedStyle(segment);
    // The window's own alignment, not a hardcoded center: regular tracks
    // center their lines but rolling ASR windows are left-aligned (and RTL
    // tracks right-aligned), and the second line should sit exactly like the
    // first. direction comes along so start/end resolve the same way.
    var winStyle = window.getComputedStyle(win);
    el.setAttribute('style', [
      'position:absolute',
      'left:' + (windowBox.left - containerBox.left) + 'px',
      // 2px under the original: the tight pairing the merged-JSON approach
      // couldn't give us, since that inherited YouTube's line-block leading.
      'top:' + (windowBox.bottom - containerBox.top + 2) + 'px',
      'width:' + windowBox.width + 'px',
      'text-align:' + (winStyle.textAlign || 'center'),
      'direction:' + (winStyle.direction || 'ltr'),
      'pointer-events:none',
      'z-index:42',
      'line-height:1.2',
      'font-size:' + cs.fontSize,
      'font-family:' + cs.fontFamily,
      'font-weight:' + cs.fontWeight,
    ].join(';'));

    // Background on the span, not the block, so it hugs the text like the
    // player's own caption rather than spanning the full caption width.
    var span = el.firstChild;
    span.setAttribute('style',
      'background:rgba(8,8,8,0.75);color:#fff;padding:0 0.25em;border-radius:2px;');
    if (span.textContent !== text) span.textContent = text;
    return;
  }

  function tick() {
    var videoId = currentVideoId();

    // SPA navigation: drop the previous video's cues before anything can draw
    // them against the new video's timeline.
    if (videoId !== startedVideoId) {
      startedVideoId = videoId;
      cues = [];
      nativeCues = [];
      cuesVideoId = null;
      pageAttempts = 0;
      askedHost = false;
      pageState = 'idle';
      removeOverlay();
      return;
    }

    if (!videoId) return;

    if (cuesVideoId !== videoId) {
      if (pageState === 'idle') {
        // loadFromPage returns false until the player publishes its track list;
        // retry until it does, and never race the in-flight fetch.
        if (!loadFromPage(videoId) && ++pageAttempts >= MAX_PAGE_ATTEMPTS) {
          pageState = 'failed';
        }
      } else if (pageState === 'failed' && !askedHost) {
        // The page never produced a usable track list. The host's InnerTube
        // lookup may still find one; its segmentation can differ from the
        // player's, so this is a last resort rather than the primary path.
        askedHost = true;
        requestCues(videoId);
      }
    }

    if (cuesVideoId !== videoId || !cues.length) return;

    // Native (AVKit) fullscreen: hand the cues to the native caption renderer
    // and stand the overlay down. The segment gate below doesn't apply here —
    // YouTube's caption DOM isn't being painted or reliably updated while the
    // page is behind AVKit — so gate on the player having a track selected.
    var fsPlayer = playerEl();
    var fsVideo = fsPlayer && fsPlayer.querySelector('video');
    if (fsVideo && fsVideo.webkitDisplayingFullscreen) {
      var captionsOn = false;
      if (typeof fsPlayer.getOption === 'function') {
        try { captionsOn = !!fsPlayer.getOption('captions', 'track').languageCode; } catch (e) { /* off */ }
      }
      syncNativeTrack(fsVideo, captionsOn);
      removeOverlay();
      return;
    }
    if (fsVideo && fsVideo.__einkbroTrack) syncNativeTrack(fsVideo, false);

    var segment = document.querySelector('.ytp-caption-segment');
    // Only draw alongside the player's own captions: if they're off or the
    // current moment has no line, a lone translation would be noise.
    if (!segment) { removeOverlay(); return; }

    // Take the timeline from the player that owns this caption, not from
    // `document.querySelector('video')`: a watch page holds several video
    // elements (the related-video inline previews autoplay in the feed), and
    // the first in DOM order is regularly not the one being watched. Reading
    // currentTime off the wrong one means no cue ever matches. During an ad the
    // player's clock is the ad's, so nothing matches and the line stays hidden
    // until playback resumes — which is what we want.
    var player = segment.closest('.html5-video-player');
    var video = player && player.querySelector('video');
    if (!video) { removeOverlay(); return; }

    var cue = cueAt(video.currentTime * 1000);
    if (!cue) { removeOverlay(); return; }
    render(cue.text, segment);
  }

  setInterval(tick, 250);
})();
