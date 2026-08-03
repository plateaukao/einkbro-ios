# Settings parity audit — 2026-07-17

Full sweep of every item on all 13 settings screens against the Android
originals (four parallel audits; per-item read-site verification). Status
after the same-day fix round. Use this as the work-list for closing the rest.

## Fixed in this round

| item | fix |
|---|---|
| Split search setting (Search) | button now opens the DataList editor (was toast) |
| Use it on TTS / `useOpenAiTts` (GPT) | two-way facade over `ttsType` (was dead storage) |
| Nav-button long-click gesture | FAB long-press now dispatches the configured action |
| Nav button position / `fabPosition` | FAB anchors Left/Center/Right; NotShow hides it |
| Show history thumbnail grid (Appearance) | pref now passed to AutoCompleteTextField |
| Save-Data header (Start) | header sent on main-document requests |
| Show tab bar (Toolbar) | live pref listener (earlier same day) |
| `saveHistoryMode` SAVE_WHEN_CLOSE | history record deferred until tab close (BrowserViewModel) |
| Highlight selection icon / font-size CSS | user-reported fixes (themed icon; html+body text-size-adjust) |
| Pull-to-refresh | live re-wire via engine setPullToRefreshEnabled |
| `shareLocation` | navigator.geolocation stubbed (PERMISSION_DENIED) when off |
| `enableRemoteAccess` | WebKit file-access KVC keys at engine creation |
| Recent bookmarks | SHOW_RECENT_BOOKMARKS renders Android's card page; clear works |
| `shouldHideToolbar` / `showToolbarFirst` | scroll auto-hide + Back-restores-toolbar |
| App locale | picker + AppleLanguages, zh-TW/zh-CN/ja/de/fr/es/ko packs shipped |
| PDF paper size | picker + paginated UIPrintPageRenderer export |
| Reader settings | reader font size/family pickers (read-sites existed) |
| Vertical toolbar | Left/Right renders the vertical rail beside the page |
| `enableZoomTextWrapReflow` | Android's reflow JS injected on page load |
| External-scheme handoff | now asks before leaving the app (x.com bounce fix) |
| Live UI pref reaction | listener now recomposes on toolbar/statusbar/FAB/hide-statusbar key changes |
| Selection-menu GPT actions | gptActionList items appended to the text-selection menu (Android ActionModeMenuViewModel parity) |
| `enableSearchSuggestion` | engine suggestions in the URL bar (SearchSuggestionFetcher; all engines via OpenSearch JSON) |
| Shortcut menu item | removed (§7 impossible) |
| Quit menu item + UI catalog entry | removed (§7); catalog unreachable |

## Remaining gaps (INERT/PARTIAL, portable — future phases)

- **`remoteQueryActionName`** — Android uses it to pick which GPT action the
  dedicated remote-query gesture runs (ActionModeDelegate:204); iOS now lists
  all GPT actions in the selection menu but has no dedicated remote-query
  entry point (tied to the unported dict/split-search flow).
- **Hide menu items editor** — prefs are honored by MenuDialog but the editor
  screen isn't built (toast).
- **Dark mode app-UI** — web content only; app chrome doesn't follow the pref.
- **`showActionMenuIcons`** — honored for link context menu only, not the
  text-selection menu.
- **Recent bookmarks** — `newTabBehavior` SHOW_RECENT_BOOKMARKS opens the full
  bookmarks dialog; `recentBookmarks` list is written but never displayed, so
  "Clear recent bookmarks" has no visible effect.
- **`autoFillForm`** — iOS exposes no WKWebView autofill switch; system
  credential autofill is always-on. Documented divergence.
- **`enableDragUrlToAction`** — Android drag-and-drop interaction model has
  no clean WKWebView mapping; deferred (item still inert).
- **`longClickAsArrowKey`** — inert (continuous arrow-repeat not implemented).
- E-ink image adjustment and vi bindings: items removed per user decision.
- **Dual caption** (Misc) — Android intercepts caption requests via
  shouldInterceptRequest, which WKWebView does not offer; needs a JS fetch-hook
  redesign. Documented divergence for now.
- **`externalSearchWithGpt`**, **`externalSearchWithPopUp`**, **`processTextUrl`**,
  **`isExternalSearchInSameTab`** — Android dict/PROCESS_TEXT flows not ported.
  The Gen AI screen's "Use AI in dictionary search" item is removed on iOS
  (Android-only); the prefs stay in `AiConfig` to mirror the Android original.
- **Live reaction (web keys)** — layout keys are now live (see fixed table);
  still applying only on next navigation instead of an immediate reload like
  Android: videoAutoplay, custom UA, darkMode. pullToRefresh needs re-wiring
  the UIRefreshControl on existing engines.

## N/A on iOS (documented, PARITY_PLAN §7)

`volumePageTurn`, `volumeDoubleClickBack` (no volume-key API);
`showDefaultActionMenu` (custom ActionModeView); Quit / move-to-background;
per-site home-screen shortcuts; APK self-update rows in About;
"share to last target" branch of `shareLongPressAction`;
`zoomInCustomView` (Android fullscreen custom-view mechanism).

Everything not listed above verified OK — real read-site on iOS matching
Android (55+ items across Behavior, Gestures, Search, UA, Data Control,
Backup, GPT screens, Start Control, Toolbar, Appearance).
