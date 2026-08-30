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

DONE (2026-07-18 overnight):
- ✅ **AI task runner** — built-in task menu + free-form agent
  (`FreeFormAgentTask` + `OpenAiRepository.chatWithTools`) wired into
  BrowserScreen; progress streams to the AI dialog.
- ✅ **FastToggle whitelist edit-icons** — open the DataList editor.
- ✅ **Analytics fast-block** — ANALYTICS_DOMAINS → WKContentRuleList.
- ✅ **hasVideo menu gating** — DOM video check on menu open.
- ✅ **DNT header** — always sent (Android parity).
- ✅ **Highlights HTML export** — writes HTML + share sheet.
- ✅ **Per-site desktop viewport width** — force_viewport_width.js injected.

Already done earlier (audit was stale): Settings entry points for AdBlock /
GPT-actions / GPT-queries / userscripts / statusbar-config / menu-item-hide are
all wired.

Remaining:
- **Instapaper credential verify** — `authenticate()` not ported (add-URL works).
- **EPUB ToC reorder/rename before save** — `EpubDialog` only does new-vs-append.

## Tier 2 — self-contained iOS-native subsystems (M)

DONE (2026-07-18 overnight):
- ✅ **Background audio + lock-screen TTS controls** — `MediaSession`
  (MPNowPlayingInfoCenter + MPRemoteCommandCenter) + `UIBackgroundModes:audio`.
- ✅ **Offline error page + retry** — `error_page.html` rendered on main-frame
  failures; `einkbro://retry` re-fetches through the nav delegate.
- ✅ **Backup: nested bookmark folders + domain configurations.**
- ✅ **Path-scoped site rules + configured-sites list** (2026-08-30) — port of
  Android `94c8194d3`/`3ffca4d84`: rule keys are `host` or `host/path/prefix`,
  every field nullable and resolved along the rule chain (`DomainConfigManager`
  rewrite, `SiteRuleKey`); Site Settings gets the "Apply to" scope picker,
  inherited-value hints, per-rule delete, CSS/JS on-off switches; Settings →
  Site Settings → Configured sites lists/edits/removes every rule. Same-document
  navigations re-apply config when the rule chain changes.
- ✅ **Restore category picker** (2026-08-30) — file import, LAN receive and
  Google Drive restore scan the zip and show Android's multi-choice
  "Select data to restore" dialog (All Preferences locks Gen AI).
- ✅ **UI theming** (2026-08-30) — port of Android feature/ui-color-themes:
  Settings → UI → Theme opens the color / border / fill picker (8 color
  themes incl. custom HSV wheel, invert toggle, 10 border styles, 8 fills
  with gradient dial). `UiThemeState` + `MyTheme` retint live; `ebItemFrame`
  / `ebDialogFrame` replace the hardcoded 1dp borders; the start page gets
  Android's `themeStyle` CSS. Dark mode Force on / Disabled now also drive
  the app chrome. Not ported: themed system splash (Android 12 API).
- ✅ **Backup: Android BackupUnit v2 layout, append-only restore** (2026-08-30)
  — export writes `_manifest.json`, `gpt_settings.json`, `database_data.json`
  (favicons/articles/highlights/AI queries/site rules), `userscripts/`,
  `transcripts.json`, `chat_sessions.json`; restore (file, LAN, Google Drive —
  incl. the Android app's Drive file) merges every table by content key and
  only fills prefs this device never set. The Backup screen is hidden until
  `einkbro://googlesync` is typed in the URL bar (`ConfigManager.isBackupRestoreUnlocked`).
  Saved pages stay out (file-backed); domain whitelists are in-memory on iOS.

Remaining:
- **App Quick Actions** (`UIApplicationShortcutItems`) — deferred: needs Swift
  lifecycle wiring + a device home-screen long-press to verify.
- **Blob downloads** — `blob:` URLs currently navigate; port
  `blob_download_hook.js` + a fetch→save bridge.
- **Custom font file import** — `FontBrowserDialog` picker is stubbed and
  `FontType.CUSTOM` renders nothing; needs `UIDocumentPicker` for the TTF and a
  `WKURLSchemeHandler` to serve it into `@font-face`.
- **Backup: export category picker** — Android lets the user choose which
  categories to back up; iOS export always writes all of them (restore has
  the picker).
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
content-rules (approximate); translate-image API key (`imageApiKey`) — dropped
with the Papago provider removal (2026-07, decided 2026-08-05), so a Papago
OCR port would need to re-add it.

## Suggested order for future sessions

1. Tier 1 wire-ups (one session, several commits).
2. Background audio + lock-screen TTS (clear, high daily value).
3. Backup completeness + bookmark folders (data-integrity).
4. Offline error page, blob downloads, custom fonts (engine finish-work).
5. App Quick Actions.
6. EPUB reader (dedicated multi-session effort).
7. Adblock engine (dedicated effort).
8. Extensions (when an Apple account/provisioning is available).
