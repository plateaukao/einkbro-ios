// Maps the Android @JavascriptInterface globals that the shared JS assets call
// onto WKScriptMessageHandler channels, so those assets run unmodified on iOS.
// Currently bridged: androidApp.getTranslation (text_node_monitor.js).
(function() {
    if (window.androidApp && window.androidApp.__ebBridged) return;
    window.androidApp = window.androidApp || {};
    window.androidApp.__ebBridged = true;
    window.androidApp.getTranslation = function(text, elementId, callback) {
        try {
            window.webkit.messageHandlers.einkbroGetTranslation.postMessage(
                JSON.stringify({ text: text, elementId: elementId, callback: callback })
            );
        } catch (e) {}
    };
})();
