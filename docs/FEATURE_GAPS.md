# EinkBro iOS — Feature-gap plan

Status: drafted 2026-07-18 from a four-domain audit of the Android app
(`/Users/maoyuankao/src/einkbro`) against this port. Companion to
`PARITY_PLAN.md` (the original 8-phase structural plan) and `SETTINGS_AUDIT.md`
(per-setting read-site audit). This document is the **feature** backlog: whole
subsystems and surfaces, prioritized for future sessions.

The port is far more complete than PARITY_PLAN's drafts imply — the entire web
engine, reader/vertical/translation/TTS/AI/userscript/EPUB-export stacks, and
all settings screens are done. What follows is what remains, by tier.

Legend: **S/M/L** effort · "blocked" = needs Apple account, new Xcode target,
or a product/key decision · "documented" = already noted as an accepted
divergence in PARITY_PLAN §7 or SETTINGS_AUDIT.

## Tier 1 — wire-ups and quick wins (S)

Existing code, one connection missing:

- **AI task runner** — port done (`task/` package); wire built-in task menu +
  custom-task path into BrowserScreen, plus `TranslationViewModel.setupTaskStream`.
  (In progress.)
- **Settings entry points** — AdBlock settings, GPT-action editor, GPT-query
  list, userscript manager, statusbar-config, menu-item-hide all exist but are
  reachable only from the retired catalog. Add Settings rows (Android has them).
- **FastToggle whitelist edit-icons** — the AdBlock/JS/Cookie pencil icons
  toast; point them at the already-built `DataList` editor (like split-search).
- **Analytics fast-block** (`blockAnalytics`) — inert pref; add the tracker
  domains to the content-rule list.
- **hasVideo menu gating** — `AudioOnly` menu row never shows because hasVideo
  is never computed; add a DOM `querySelector('video')` check on page finish.
- **DNT header** — send `DNT: 1` alongside the existing Save-Data header.
- **Highlights HTML export** — the export button toasts; the HTML dump
  functions exist, just need a file write + share sheet (`FileStore`).
- **Per-site desktop viewport width** — pref/DB/SiteSettings exist but
  `force_viewport_width.js` is never injected; inject on load.
- **Instapaper credential verify** — `authenticate()` not ported (add-URL works).
- **EPUB ToC reorder/rename before save** — `EpubDialog` only does new-vs-append.

## Tier 2 — self-contained iOS-native subsystems (M)

Genuinely new work, but well-scoped and unattended-safe (no new Xcode target):

- **Background audio + lock-screen TTS controls** — highest-value gap in the
  input/system domain. `MPNowPlayingInfoCenter` + `MPRemoteCommandCenter` for
  play/pause/next from Control Center and headsets, plus `UIBackgroundModes:
  audio` in Info.plist so TTS/AI audio survives backgrounding. Nothing exists
  today (zero MPNowPlaying refs).
- **App Quick Actions** (`UIApplicationShortcutItems`) — launcher long-press
  shortcuts (new tab, bookmarks, incognito). Android has the analog; §7 says
  app-level quick actions are feasible (only *per-site* home icons are not).
- **Offline error page + retry** — port `error_page.html` + `einkbro://retry`
  and the https→http fallback on SSL-protocol errors; today `onLoadError` only
  toasts.
- **Blob downloads** — `blob:` URLs currently navigate; port
  `blob_download_hook.js` + a fetch→save bridge.
- **Custom font file import** — `FontBrowserDialog` picker is stubbed and
  `FontType.CUSTOM` renders nothing; needs `UIDocumentPicker` for the TTF and a
  `WKURLSchemeHandler` to serve it into `@font-face`.
- **Backup completeness** — the backup ZIP covers only prefs+bookmarks+history;
  Android also backs up favicons, articles/highlights, chatGptQueries, domain
  configs, saved pages, and whitelist domains. Add those tables + a category
  picker. Also: Chrome/Netscape bookmark import is flat — port nested folders.
- **Tap-to-select sentence/paragraph** — context-menu "Select text" toasts;
  port `select_sentence.js`/`select_paragraph.js`.
- **SiteSettings per-site CSS/JS editor** — the text editor is stubbed; wire
  the existing `TextEditorDialog`.
- **Userscript `@require`/`@resource` + `GM.*` promise API + `GM_fetch`** —
  metadata parses `requires` but the manager ignores them, and only the
  callback-style `GM_xmlhttpRequest` exists; breaks jQuery-style scripts.
- **Hardware-keyboard arrow paging** — `useUpDownPageTurn` inert for keys;
  `UIKeyCommand`/`onPreviewKeyEvent` on the web host for external keyboards.
- **Naver dictionary webview** — the webview half is portable (the colordict
  intent half is Android-only).
- **Selection menu polish** — separate DeepL/Papago/Naver translate entries,
  "read from here", long-press-Highlight→restyle dialog, long-press-GPT-action
  →edit.
- **Video PiP / continueMedia read-sites** — prefs inert; wire to the engine.
- **Open-in for .mht/.srt/.epub files** — only `.webarchive` is a registered
  document type.

## Tier 3 — large or blocked (L / needs decision)

- **EPUB reader/viewer** (L) — the single biggest missing subsystem. Export
  works; there is no in-app reader (page nav, per-chapter TOC, in-book
  highlight). WKWebView can't render epub directly — needs an unzip →
  per-chapter XHTML → reader-pipeline renderer.
- **Adblock filter-list engine** (L) — the AdBlock settings screen is an
  in-memory facade; only 66 static URL rules ship. Needs real filter-list
  fetch/parse (ABP + hosts syntax) → WKContentRuleList compilation, plus
  cosmetic/element-hiding (`##` → css-display-none).
- **Papago translate-by-screen OCR** (M/L) — `translateByScreen(bytes)` exists
  but nothing feeds it a WKSnapshot; the two-pane screen-capture flow is unbuilt.
- **Share Extension** (blocked) — receiving shared text/URLs *into* EinkBro
  needs a new app-extension target + provisioning. Same for the **Action
  Extension** behind PROCESS_TEXT / dict / external-search.
- **Default-browser role** (blocked) — needs Apple's managed entitlement.
- **Client-certificate (mutual TLS)** picker (M, niche).

## Documented divergences (no action — see PARITY_PLAN §7 / SETTINGS_AUDIT)

Volume-key paging & volume-back (no API); vi bindings & e-ink DEEP image mode
(removed per user); per-site home-screen shortcuts, move-to-background,
share-to-last-target, forced rotation (constrained), new-window/ExtraBrowser,
default text-selection menu, UA client-hints, drag-URL-to-action, autofill
switch, form-autofill; MHT/save-for-later lossy → webarchive; cookie gating via
content-rules (approximate).

## Suggested order for future sessions

1. Tier 1 wire-ups (one session, several commits).
2. Background audio + lock-screen TTS (clear, high daily value).
3. Backup completeness + bookmark folders (data-integrity).
4. Offline error page, blob downloads, custom fonts (engine finish-work).
5. App Quick Actions.
6. EPUB reader (dedicated multi-session effort).
7. Adblock engine (dedicated effort).
8. Extensions (when an Apple account/provisioning is available).
