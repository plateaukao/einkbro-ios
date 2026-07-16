/*
 * Google website-translate widget (parity Phase M), ported from Android's
 * INJECT_GOOGLE_TRANSLATE_V2_JS_FORMAT in WebViewJsBridge. Loads Google's
 * TranslateElement (translate_a/element.js) in auto in-place mode: it rewrites
 * the page's text nodes directly, no container div needed. `autoDisplay:false`
 * + `floatPosition:0` keep the widget banner minimal. `%%INCLUDED_LANGUAGES%%`
 * is replaced by the host with `includedLanguages: 'xx,yy',` (from
 * preferredTranslateLanguageString) or an empty string.
 */
(function () {
  function showBanner() {
    window.setTimeout(function () { window[teKey].showBanner(true); }, 10);
  }
  function makeElement() {
    return new google.translate.TranslateElement({
      autoDisplay: false,
      floatPosition: 0,
      %%INCLUDED_LANGUAGES%%
      pageLanguage: 'auto'
    });
  }
  var teKey = 'TE_7777';
  var cbKey = 'TECB_7777';
  if (window[teKey]) {
    showBanner();
  } else if (!window.google || !google.translate || !google.translate.TranslateElement) {
    if (!window[cbKey]) {
      window[cbKey] = function () { window[teKey] = makeElement(); showBanner(); };
    }
    var script = document.createElement('script');
    script.src = 'https://translate.google.com/translate_a/element.js?cb=' +
      encodeURIComponent(cbKey) + '&client=tee';
    document.getElementsByTagName('head')[0].appendChild(script);
    // Shrink the widget's own font on e-ink after it mounts (~1s).
    setTimeout(function () {
      var css = document.createElement('style');
      css.type = 'text/css';
      css.charset = 'UTF-8';
      css.appendChild(document.createTextNode(
        '.goog-te-combo, .goog-te-banner *, .goog-te-ftab *, .goog-te-menu *, ' +
        '.goog-te-menu2 *, .goog-te-balloon * {font-size: 8pt !important;}'));
      var teef = document.getElementById(':0.container');
      if (teef) { teef.contentDocument.head.appendChild(css); }
    }, 1000);
  }
})();
