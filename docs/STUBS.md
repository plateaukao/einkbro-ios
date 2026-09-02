# Stub inventory

Every place where the iOS port still shows a placeholder, a "would…" toast, a
no-op, or seeded sample data instead of the Android behavior. Verified against
the source on 2026-09-02 (grep for `would `, `stub`, `stand-in`, `later phase`,
`catalog`, `sample`, `representative`, `comingSoon`, plus a read-site sweep of
the prefs the parity plan lists as inert). Android reference paths are relative
to `app/src/main/java/info/plateaukao/einkbro/` in `../einkbro`.

Compat shims are **not** stubs and must stay: `android/content/Context.kt`,
`SharedPreferences.kt`, `android/graphics/Point.kt`, `util/Uri.kt`,
`util/Locale.kt`, `util/System.kt`, `util/PlatformCompat.kt`,
`util/NoDimDialog.kt`, `view/dialog/DialogManager.kt`, `view/EBToast.kt`,
`view/Album.kt`, `setting/screens/SettingScreenDeps.kt`. They exist so Android
files compile unchanged (see CLAUDE.md).

## 1. User-facing gaps (a tap does nothing, toasts, or lies)

| # | Feature | iOS site | Android behavior | What's needed |
|---|---------|----------|------------------|---------------|
| 1 | Site Settings: edit per-site CSS / JS | `view/dialog/compose/SiteSettingsDialog.kt:125` toasts "would open text editor" | `TextEditorDialogFragment` | A multi-line text editor dialog (NoDimAlertDialog + TextField) writing back through `onSave` |
| 2 | Translate popup: target-language picker | `view/dialog/compose/TranslateDialog.kt:101` toasts | `TranslationLanguageDialog` then re-translate | Reuse the existing `TranslationLanguageDialog` select-option flow, then `translationViewModel.translate()` |
| 3 | Translate popup: tap result to load it in the WebView | `view/dialog/compose/TranslateDialog.kt:506` toasts | loads `webContent` into the tab | Host callback → `browserViewModel.loadUrlOrSearch` / `loadHtml` |
| 4 | GPT query history: export as HTML | `activity/GptQueryList.kt:76` toasts | writes an HTML file and shares it | Build HTML, `FileStore.writeBytes` + `FileStore.share` |
| 5 | Arrow-key paging actions (`SendLeftKey` / `SendRightKey`) | `view/compose/BrowserScreen.kt:579-580` "coming in Phase F" | dispatches key events to the WebView | JS page-turn (same as touch paging) — there is no key injection on iOS |
| 6 | Remote text search (`ToggleTextSearch` / `ToggleReceiveTextSearch`) | `view/compose/BrowserScreen.kt:680-681` "coming in Phase J" | LAN text-search service | Needs the multicast entitlement (see CLAUDE.md gotchas); wire on top of `LanShare` once granted |
| 7 | Data Control → JavaScript / Cookie / AdBlock whitelists | `browser/AdBlock.kt`, `browser/Cookie.kt`, `browser/Javascript.kt`, `browser/DomainInterface.kt` (in-memory `BaseWebConfig`, seeded with fake domains, lost on restart, never consulted by the engine) | Room-backed whitelist tables read by `NinjaWebViewClient` | Back the three lists with the per-site `DomainConfig` table (`config.updateDomainConfig`) or a Room table, and drop the seed lists |
| 8 | AdBlock filter-list manager | `browser/AdBlock.kt` `FilterViewModel`: in-memory list, `download()` is `delay(400)/delay(800)` then "success"; `ContentBlocker` only compiles the bundled `adblock_rules.json` | `io.github.edsuns.adfilter` downloads EasyList-style lists | Download the list, convert to WKContentRuleList JSON (or bundle a converter), feed `ContentBlocker.preload` |
| 9 | Whitelist edit: cancel shows "text input dialog is not available in the catalog" | `activity/DataList.kt:111`, `:195` | — | Stale: the text-input dialog exists now; cancel must just return |
| 10 | Edge-TTS voice picker | `view/dialog/compose/ETtsVoiceDialog.kt` lists 6 hard-coded sample voices | parses `assets/eVoiceList.json` (155 KB, all voices) | Copy `eVoiceList.json` into `composeResources/files`, parse with kotlinx-serialization |
| 11 | Status bar battery | `view/statusbar/Statusbar.kt:86` fixed 80 % | polls `BatteryManager` | expect/actual on `UIDevice.batteryLevel` (enable `batteryMonitoringEnabled`) |
| 12 | Split-search list seeded with Wikipedia / Jisho when empty | `search/SplitSearchListType.kt:48` | no seeding | Delete the seed block (catalog artifact leaking into production) |
| 13 | Geolocation with `shareLocation` on | `iosMain/browser/WKWebViewEngine.kt:167`, `:1033` block it when off (parity); when on, Info.plist has no `NSLocationWhenInUseUsageDescription`, so WKWebView can never grant | prompts via `GeolocationPermissions` | Add the usage string to `project.yml`, implement the WKUIDelegate permission callback |
| 14 | E-ink image adjustment (`einkImageAdjustment` / `einkImageMode`) | pref is written by `SettingComposeUi.kt` but has **no read site** outside settings; `EinkImageProcessor` in `unit/UnitStubs.kt` is an empty object | FAST mode = CSS `img { filter: … }` in `WebViewReaderHelper.einkImageFilterCss`; QUALITY = native re-encode | Port `einkImageFilterCss` into `WebContentHelper.updateCssStyle` (FAST); QUALITY needs a WKURLSchemeHandler or stays FAST-only |
| 15 | App-locale picker in `TranslationLanguageDialog.showAppLocale` | `view/dialog/TranslationLanguageDialog.kt:57` toasts | — | Dead path: `setting/screens/UiSettings.kt` implements the picker inline; delete the stub method |

## 2. Inert preferences (UI persists, nothing reads them)

Read-site sweep outside `preference/`, `setting/`, `activity/Setting*`, `catalog/`.
Zero hits today:

| Pref | Android read site | Note |
|------|-------------------|------|
| `autoFillForm` | `EBWebView` WebSettings | WKWebView has no toggle; would need JS `autocomplete=off` injection |
| `useUpDownPageTurn` | touch-area handler | wire in `TouchGestureOverlay` |
| `enableMultitouch` | `MultitouchListener` | pinch/three-finger gestures in the Compose overlay |
| `enableVolumePageTurn` | volume keys | n/a on iOS (no volume-key API) — document as unsupported |
| `einkImageAdjustment`, `einkImageMode` | see §1 #14 | |
| `fontFolderUri` | SAF folder picker | superseded on iOS by the Documents/fonts store (see §4) |

Everything else the parity plan listed (`keepAwake`, `hideStatusbar`,
`enableImages`, `getEnableCookies`, `shareLocation`, `enableCertificateErrorDialog`,
`webLoadCacheFirst`, `enableSaveData`, `debugWebView`, `enableRemoteAccess`,
`longClickAsArrowKey`, `touchAreaHint`, `hideTouchAreaWhenInput`,
`switchTouchAreaAction`, `disableLongPressTouchArea`) now has at least one read site.

## 3. Platform limitations surfaced as toasts (not fixable 1:1)

| Site | Message | Why |
|------|---------|-----|
| `view/handlers/ToolbarActionHandler.kt:23` | "Multiple app windows aren't supported on iOS" | would need UIScene multi-window support |
| `view/handlers/ToolbarActionHandler.kt:60` | "iOS apps can't send themselves to the background" | no public API |
| `view/compose/BrowserScreen.kt:833` | "Rotate your device — iOS controls orientation" | could lock via `supportedInterfaceOrientations`; not ported |
| `view/compose/BrowserScreen.kt:1083` | "Long-press the text itself to select on iOS" | WKWebView owns selection |

## 4. Dead helpers in `unit/UnitStubs.kt` (zero live callers)

`IntentUnit.showFile` (toast), `IntentUnit.createResultLauncher` (null),
`IntentUnit.gotoSettings` (no-op), `IntentUnit.readCurrentArticle` (no-op),
`ShareUtil.startServingFile` / `startReceivingFile` / `stopBroadcast` (no-op;
`LanShare` is the real seam), `HelperUnit.getStringFromAsset` (returns ""),
`BrowserUnit.createFilePicker` (null), `BrowserUnit.restartApp` (toast),
`BackupUnit` (no-op; `BackupOps` is the real seam), `LocaleManager.setLocale`
(no-op), `EinkImageProcessor` (empty object). They are only referenced from
comments; each is either a planned seam (§1) or can be removed once its
Android caller is confirmed unported.

## 5. Stale comments (behavior is real, comment still says "stub")

- `view/dialog/compose/TtsSettingDialog.kt:68` — "stub view model pre-seeded"; `TtsViewModel` is the real one.
- `view/dialog/compose/TranslateDialog.kt:79` — "stub view model fakes a translation"; `TranslationViewModel` calls the real APIs.
- `activity/AdBlockSetting.kt:50` — describes the in-memory filter model (§1 #8), accurate but should point here.

## Resolved

- **Custom fonts** (2026-09-02): the font browser lists real font files from
  `Documents/fonts` (added through the system file picker), previews each with
  the real typeface, and the selected font is applied to pages through an
  `@font-face` data URL in `WebContentHelper`; the font dialog's settings icon
  opens the browser and "Custom" font scale prompts for a value, for both
  normal and reader mode. Previously: sample list, folder-picker toast,
  `FontType.CUSTOM -> ""`.
