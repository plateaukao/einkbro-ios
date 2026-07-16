/*
 * Dual YouTube captions (parity Phase M). Android intercepts the `timedtext`
 * network request in the WebViewClient and merges a second-language copy into
 * the caption JSON (DualCaptionProcessor). WKWebView can't intercept page
 * subresources natively, so this document-start shim patches window.fetch and
 * XMLHttpRequest in-page: when a `timedtext` request is seen it also fetches the
 * `&tlang=<locale>` variant and merges the two, appending the translated line
 * under each original caption line. The merge algorithm is a 1:1 port.
 * Host replaces %%DUAL_CAPTION_LOCALE%% (empty string disables the feature).
 */
(function () {
  var LOCALE = '%%DUAL_CAPTION_LOCALE%%';
  if (!LOCALE) return;
  if (window.__einkbroDualCaptionInstalled) return;
  window.__einkbroDualCaptionInstalled = true;

  function isCaptionUrl(u) {
    return typeof u === 'string' && u.indexOf('timedtext') !== -1;
  }

  function translatedUrl(u) {
    return u + '&tlang=' + LOCALE;
  }

  function mergeCaptions(originalText, translatedText) {
    try {
      var orig = JSON.parse(originalText);
      var trans = JSON.parse(translatedText);
      if (orig.wsWinStyles) {
        orig.wsWinStyles.forEach(function (s) {
          if (s.mhModeHint != null) s.mhModeHint = 0;
          if (s.sdScrollDir != null) s.sdScrollDir = 0;
        });
      }
      if (orig.events) {
        orig.events.forEach(function (event) {
          if (event.segs && event.segs.length > 0) {
            var first = event.segs[0];
            first.utf8 = event.segs.map(function (s) { return s.utf8; }).join('');
            var tEvent = (trans.events || []).filter(function (e) {
              return e.tStartMs === event.tStartMs;
            })[0];
            if (tEvent && tEvent.segs && tEvent.segs.length > 0) {
              first.utf8 += '\n' + tEvent.segs.map(function (s) { return s.utf8; }).join('');
            }
            event.segs = [first];
          }
        });
      }
      return JSON.stringify(orig);
    } catch (e) {
      return originalText;
    }
  }

  // ── fetch ────────────────────────────────────────────────────────────────
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var url = (typeof input === 'string') ? input : (input && input.url);
      if (!isCaptionUrl(url)) return origFetch.apply(this, arguments);
      return origFetch(input, init).then(function (resp) {
        return resp.clone().text().then(function (origText) {
          if (!origText) return resp;
          return origFetch(translatedUrl(url), init)
            .then(function (tResp) { return tResp.text(); })
            .then(function (tText) {
              var merged = mergeCaptions(origText, tText);
              return new Response(merged, {
                status: resp.status,
                statusText: resp.statusText,
                headers: resp.headers,
              });
            })
            .catch(function () { return resp; });
        });
      });
    };
  }

  // ── XMLHttpRequest ─────────────────────────────────────────────────────────
  var OrigXHR = window.XMLHttpRequest;
  if (OrigXHR) {
    var Patched = function () {
      var xhr = new OrigXHR();
      var reqUrl = null;
      var origOpen = xhr.open;
      xhr.open = function (method, u) {
        reqUrl = u;
        return origOpen.apply(xhr, arguments);
      };
      var origSend = xhr.send;
      xhr.send = function (body) {
        if (!isCaptionUrl(reqUrl)) return origSend.apply(xhr, arguments);
        Promise.all([
          fetch(reqUrl).then(function (r) { return r.text(); }),
          fetch(translatedUrl(reqUrl)).then(function (r) { return r.text(); })
            .catch(function () { return ''; }),
        ]).then(function (parts) {
          var merged = parts[1] ? mergeCaptions(parts[0], parts[1]) : parts[0];
          Object.defineProperty(xhr, 'responseText', { value: merged, configurable: true });
          Object.defineProperty(xhr, 'response', { value: merged, configurable: true });
          Object.defineProperty(xhr, 'readyState', { value: 4, configurable: true });
          Object.defineProperty(xhr, 'status', { value: 200, configurable: true });
          xhr.dispatchEvent(new Event('readystatechange'));
          xhr.dispatchEvent(new Event('load'));
        }).catch(function () {
          origSend.call(xhr, body);
        });
      };
      return xhr;
    };
    Patched.prototype = OrigXHR.prototype;
    window.XMLHttpRequest = Patched;
  }
})();
