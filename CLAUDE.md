# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

iOS port of the [EinkBro](https://github.com/plateaukao/einkbro) Android e-ink browser, built with Compose Multiplatform (Kotlin 2.1, CMP 1.8). The Android original lives at `/Users/maoyuankao/src/einkbro` and is the behavioral reference — when implementing or fixing a feature, read the Android implementation first and copy its behavior. Ported code keeps the original `info.plateaukao.einkbro.*` package layout so files can be diffed against their Android counterparts.

The app boots into a working WKWebView-backed browser (`BrowserScreen`); the original UI-catalog mode (a list of every ported screen/dialog rendering with sample data) is still reachable from the browser and serves as a living style guide.

## Commands

```bash
# Fast verify loop: type-check the shared Kotlin module (this is the primary check — no test suite exists)
./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | grep -E "^e: "

# Regenerate the Xcode project — required after editing iosApp/project.yml
# (project.pbxproj is GENERATED; never hand-edit it)
cd iosApp && xcodegen generate

# Full simulator build (the pre-build phase compiles the Kotlin framework too)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

There are no unit tests. Verification is: compile passes, then visually drive the feature in the iOS simulator.

## Architecture

Single Gradle module `composeApp` with iOS-only targets (`iosSimulatorArm64`, `iosArm64`) producing a static framework `ComposeApp`, consumed by a thin SwiftUI wrapper in `iosApp/` (XcodeGen project; `project.yml` is the source of truth).

### Source sets and the platform seam

- `composeApp/src/commonMain/kotlin/info/plateaukao/einkbro/` — all UI, ViewModels, preference layer (`ConfigManager`), Room KMP database, Ktor networking (translate/OpenAI/Gemini/Edge-TTS), JS/CSS assets shared with Android.
- `composeApp/src/commonMain/kotlin/android/`, `androidx/` — tiny source-compatible shims (e.g. `SharedPreferences`, `Context`) that let original Android files compile unchanged. These are load-bearing; don't "clean them up".
- `composeApp/src/iosMain/` — `actual` implementations for the expect/actual seams. The important ones: `WebViewEngine` (common interface) → `WKWebViewEngine.kt` (wraps WKWebView, user scripts, JS message handlers), `PrefsStore` (NSUserDefaults), `TtsManager`/`AudioPlayer` (AVFoundation), `FilePicker`/`FileStore`, `ContentBlocker` (WKContentRuleList), `HttpClientProvider` (Ktor Darwin), `Crypto`, `LanShare`, `HostBridge`. Find all seams with `grep -rl "expect " composeApp/src/commonMain`.

Entry chain: `iOSApp.swift` → `MainViewController.kt` (iosMain; also `handleExternalUrl` for the `einkbro://` scheme and hand-offs) → `App()` (commonMain) → `BrowserScreen`.

### Key patterns

- **DI**: `AppServices` (commonMain root) is a plain object owning the session singletons (`config`, `bookmarkManager`, `dialogManager`, `database`, …). A Koin container is started only so legacy `KoinComponent` code resolves; new code should reach through `AppServices` directly.
- **Dialogs**: `DialogManager` exposes pending-request Compose state (`pendingOkCancel`, `pendingSelectOption`, `pendingTextInput`); host composables in `App.kt` render them. Larger dialogs are plain composables driven by `showXxx` booleans in `BrowserScreen`.
- **Toasts**: `EBToast.show(context, "...")` renders via the overlay in `App.kt`. It is also the convention for stubbing actions that can't work on iOS — show what would happen instead of crashing.
- **Action dispatch**: browser actions funnel through the `BrowserAction` sealed class (`browser/BrowserAction.kt`); `BrowserScreen.kt` (~1800 lines) hosts toolbar/menu handling and overlay state, `BrowserViewModel` owns tabs (`Album`) and engine calls. Android's equivalents are `ToolbarActionHandler`/`MenuActionHandler`/`BrowserActivity` — mirror their mappings.
- **Resources**: compose resources with `Res` class `info.plateaukao.einkbro.resources` (public). Android `R.string.x`/`R.drawable.x` become `Res.string.x`/`Res.drawable.x`; strings and vector drawables were converted from the Android `res/` tree into `composeResources/`.

## Porting workflow

`tools/PORTING.md` is the authoritative convention document for porting a file from the Android tree — read it before porting anything. Mechanical transforms are automated by `python3 tools/port.py <file.kt>` (resource refs, imports, annotation removal; prints `WARN` for manual work). Core rules that always apply:

- Never delete a composable to make things compile — simplify its data plumbing, not its layout.
- Resource-id `Int`s become `StringResource`/`DrawableResource` (sentinel `0` → nullable).
- `by inject()` → `AppServices.*`; `SimpleDateFormat` → `util.DateFormat.format`.

## Plans and status docs

- `docs/MIGRATION_PLAN.md` — the 8 structural phases turning the catalog into a real browser (all implemented; documents the target architecture and per-phase design, including the WebViewEngine seam).
- `docs/PARITY_PLAN.md` — current work: full feature parity for toolbar/menus/settings, phased A–N with Android file/line references per phase. Notes which settings are "present-but-inert" (UI persists but nothing reads the pref) and which features only need dispatcher wiring. Consult the relevant phase before starting feature work.

## Gotchas

- `iosApp/project.yml` intentionally omits the entitlements block: LAN share uses UDP multicast, which needs Apple's restricted multicast entitlement (not yet granted for the team). Restore the commented block in `project.yml` only once granted, else provisioning fails.
- Room KMP with `sqlite-bundled` and KSP: schema files live in `composeApp/schemas/`; the KSP compiler is wired per-target (`kspIosSimulatorArm64`, `kspIosArm64`).
