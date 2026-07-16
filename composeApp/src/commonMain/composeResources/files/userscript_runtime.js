// EinkBro userscript runtime (parity Phase H).
//
// Installed once per web view at document-start. It defines the GM_* API hub and
// window.__einkbroInject(descriptors), which the host calls (via
// evaluateJavascript) once the page has loaded, passing every enabled script's
// compiled match regexes, GM_info, stored-value snapshot, and body. Each
// descriptor whose regexes match location.href runs with its own GM_* bound to
// its script id. All native round-trips go through the single string channel
// window.webkit.messageHandlers.einkbroGm.
(function () {
    if (window.__einkbroGM) return;

    function post(obj) {
        try {
            window.webkit.messageHandlers.einkbroGm.postMessage(JSON.stringify(obj));
        } catch (e) { /* handler not registered (e.g. sub-frame) */ }
    }

    var hub = {
        seq: 1,
        menuCallbacks: {},
        xhrCallbacks: {},
        invokeMenu: function (fnId) {
            var fn = hub.menuCallbacks[fnId];
            if (typeof fn === 'function') {
                try { fn(); } catch (e) { console.error('[einkbro] menu command failed', e); }
            }
        },
        // Called by the host after an async GM_xmlhttpRequest completes.
        handleXhr: function (reqId, event, payloadJson) {
            var cbs = hub.xhrCallbacks[reqId];
            if (!cbs) return;
            var resp;
            try { resp = payloadJson ? JSON.parse(payloadJson) : {}; } catch (e) { resp = {}; }
            var handler = cbs['on' + event];
            if (typeof handler === 'function') {
                try { handler(resp); } catch (e) { console.error('[einkbro] GM_xhr callback failed', e); }
            }
            if (event === 'load' || event === 'error' || event === 'timeout' || event === 'abort') {
                delete hub.xhrCallbacks[reqId];
            }
        },
    };
    window.__einkbroGM = hub;

    function testAny(sources, href) {
        for (var i = 0; i < sources.length; i++) {
            try { if (new RegExp(sources[i]).test(href)) return true; } catch (e) { /* bad regex */ }
        }
        return false;
    }

    function matchesUrl(d, href) {
        if (href.indexOf('http') !== 0) return false;
        var included = testAny(d.matches, href) || testAny(d.includes, href);
        if (!included) return false;
        if (d.excludes && d.excludes.length && testAny(d.excludes, href)) return false;
        return true;
    }

    function buildGm(d) {
        var values = d.values || {};
        var api = {};

        api.GM_getValue = function (key, def) {
            if (Object.prototype.hasOwnProperty.call(values, key)) {
                try { return JSON.parse(values[key]); } catch (e) { return values[key]; }
            }
            return def;
        };
        api.GM_setValue = function (key, value) {
            values[key] = JSON.stringify(value);
            post({ type: 'set', scriptId: d.id, key: key, value: values[key] });
        };
        api.GM_deleteValue = function (key) {
            delete values[key];
            post({ type: 'delete', scriptId: d.id, key: key });
        };
        api.GM_listValues = function () { return Object.keys(values); };

        api.GM_addStyle = function (css) {
            var el = document.createElement('style');
            el.textContent = css;
            (document.head || document.documentElement).appendChild(el);
            return el;
        };
        api.GM_addElement = function (parentOrTag, tagOrAttrs, maybeAttrs) {
            var parent, tag, attrs;
            if (typeof parentOrTag === 'string') { parent = document.head || document.documentElement; tag = parentOrTag; attrs = tagOrAttrs; }
            else { parent = parentOrTag; tag = tagOrAttrs; attrs = maybeAttrs; }
            var el = document.createElement(tag);
            if (attrs) Object.keys(attrs).forEach(function (k) {
                if (k === 'textContent') el.textContent = attrs[k]; else el.setAttribute(k, attrs[k]);
            });
            (parent || document.documentElement).appendChild(el);
            return el;
        };

        api.GM_log = function () { console.log.apply(console, ['[US]'].concat([].slice.call(arguments))); };
        api.GM_setClipboard = function (text) { post({ type: 'clipboard', text: String(text) }); };
        api.GM_openInTab = function (url, opts) {
            var active = !(opts && (opts.active === false || opts === true));
            post({ type: 'opentab', url: url, active: active });
            return { close: function () {} };
        };
        api.GM_notification = function (arg) {
            var text = (arg && arg.text) ? arg.text : String(arg);
            post({ type: 'notify', text: text });
        };
        api.GM_registerMenuCommand = function (caption, fn) {
            var fnId = 'm' + (hub.seq++);
            hub.menuCallbacks[fnId] = fn;
            post({ type: 'menu', caption: String(caption), fnId: fnId });
            return fnId;
        };
        api.GM_unregisterMenuCommand = function (fnId) { delete hub.menuCallbacks[fnId]; };

        api.GM_xmlhttpRequest = function (details) {
            var reqId = 'x' + (hub.seq++);
            hub.xhrCallbacks[reqId] = {
                onload: details.onload, onerror: details.onerror,
                ontimeout: details.ontimeout, onabort: details.onabort,
            };
            post({
                type: 'xhr', reqId: reqId, scriptId: d.id, connects: d.connects || [],
                method: (details.method || 'GET').toUpperCase(), url: details.url,
                headers: details.headers || {}, data: details.data != null ? String(details.data) : null,
                timeout: details.timeout || 0, responseType: details.responseType || '',
            });
            return { abort: function () {} };
        };

        api.GM_info = d.info;
        api.unsafeWindow = window;

        // Promisified GM.* facade over the callback API.
        var GM = {
            info: d.info,
            getValue: function (k, def) { return Promise.resolve(api.GM_getValue(k, def)); },
            setValue: function (k, v) { api.GM_setValue(k, v); return Promise.resolve(); },
            deleteValue: function (k) { api.GM_deleteValue(k); return Promise.resolve(); },
            listValues: function () { return Promise.resolve(api.GM_listValues()); },
            addStyle: function (c) { return api.GM_addStyle(c); },
            setClipboard: function (t) { api.GM_setClipboard(t); },
            registerMenuCommand: api.GM_registerMenuCommand,
            notification: api.GM_notification,
            openInTab: api.GM_openInTab,
            xmlHttpRequest: function (details) {
                return new Promise(function (resolve, reject) {
                    var d2 = {};
                    for (var k in details) d2[k] = details[k];
                    d2.onload = resolve; d2.onerror = reject; d2.ontimeout = reject;
                    api.GM_xmlhttpRequest(d2);
                });
            },
        };
        api.GM = GM;
        return api;
    }

    function runScript(d) {
        var api = buildGm(d);
        var names = Object.keys(api);
        var body = d.body + '\n//# sourceURL=einkbro-userscript-' + d.id + '.user.js';
        try {
            var fn = new Function(names.join(','), body);
            fn.apply(window, names.map(function (n) { return api[n]; }));
        } catch (e) {
            console.error('[einkbro] userscript ' + d.id + ' failed', e);
        }
    }

    window.__einkbroInject = function (list) {
        if (!Array.isArray(list)) return;
        var href = location.href;
        window.__einkbroDone = window.__einkbroDone || {};
        for (var i = 0; i < list.length; i++) {
            var d = list[i];
            if (window.__einkbroDone[d.id]) continue;
            if (!matchesUrl(d, href)) continue;
            window.__einkbroDone[d.id] = true;
            runScript(d);
        }
    };
})();
