# EinkBro iOS

iOS port of [EinkBro](https://github.com/plateaukao/einkbro)'s Jetpack Compose UI,
built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

The Android app's Compose UI (toolbar, tab/history overview, settings screens,
dialogs) runs as common Kotlin; Android-only services (WebView, TTS, file pickers,
SharedPreferences) are backed by in-memory shims so the UI is fully explorable.

The app currently launches as a **UI catalog**: a list of every ported screen and
dialog, each rendering with representative sample data, for visual verification on
iOS. The real preference layer is ported (not stubbed), so settings toggles work
and persist for the session.

## Structure

- `composeApp/` — shared Kotlin Multiplatform module (all UI in `commonMain`)
  - `src/commonMain/kotlin/info/plateaukao/einkbro/` — ported EinkBro code,
    same package layout as the Android app
  - `src/commonMain/kotlin/android/`, `androidx/` — tiny source-compatible shims
    (in-memory SharedPreferences, no-op Room annotations) that let the original
    preference/database files compile unchanged
  - `src/commonMain/composeResources/` — strings + vector drawables converted
    from the Android res/ tree
- `iosApp/` — SwiftUI wrapper app (XcodeGen project)
- `tools/PORTING.md` — porting conventions; `tools/port.py` — Android→CMP codemod

## Build & run

```bash
# type-check the shared module
./gradlew :composeApp:compileKotlinIosSimulatorArm64

# generate the Xcode project (once, or after project.yml changes)
cd iosApp && xcodegen generate

# build for the iOS simulator
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

Or open `iosApp/iosApp.xcodeproj` in Xcode and hit Run.
