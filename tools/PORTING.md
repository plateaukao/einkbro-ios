# EinkBro Android → Compose Multiplatform (iOS) porting conventions

Source project: `/Users/maoyuankao/src/einkbro` (Android).
Target project: `/Users/maoyuankao/src/einkbro-ios` (Kotlin Multiplatform, iOS-only).
All ported code goes to `composeApp/src/commonMain/kotlin/` keeping the original
`info.plateaukao.einkbro.*` package structure.

## Goal

Port the **Compose UI faithfully** so it can be verified visually on iOS. Behavior
that needs Android services (WebView, TTS engines, file pickers, printing) is
stubbed — visible UI must render with representative data; interactions that
can't work show a toast (`EBToast.show(context, "...")`) instead of crashing.

## Verify loop

```bash
cd /Users/maoyuankao/src/einkbro-ios   # or your isolated copy
./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | grep -E "^e: "
```

## Step 1: copy + codemod

Copy the assigned file(s) from the Android tree to the same package path, then:

```bash
python3 tools/port.py <file.kt> [...]
```

The codemod handles mechanically: `R.string/R.drawable` → `Res.string/Res.drawable`
(+ star import of `info.plateaukao.einkbro.resources`), compose resource imports →
`org.jetbrains.compose.resources.*`, `stringResource(id = X)` → `stringResource(X)`,
`@Preview`/`@SuppressLint`/`@StringRes` removal, `LocalContext`/`LocalConfiguration`
swaps, `Bitmap` → `ImageBitmap`, `System.currentTimeMillis` shim import.
It prints `WARN` lines for things needing manual work.

## Step 2: manual conventions

- **DialogFragment / Activity wrappers**: delete the class; keep every `@Composable`
  (including private ones and `@Preview` bodies — previews become plain composables,
  rename to `Preview<Thing>` public if useful for the catalog). Top-level composable
  must be callable with stub data: give it parameters with defaults where the class
  provided fields. Keep the original file name minus `Fragment` (e.g.
  `MenuDialog.kt` from `MenuDialogFragment.kt`).
- **Resource-id Ints**: any `val xxxResId: Int` → `StringResource` / `DrawableResource`
  (`org.jetbrains.compose.resources`). Sentinel `0` → nullable + `null`; `!= 0` → `!= null`.
- **Icons**: `ImageVector.vectorResource(id = x)` → `vectorResource(x)`.
- **`context.getString(res)`** compiles as-is via extension
  `info.plateaukao.einkbro.util.getString` (codemod adds import). Where no `context`
  exists use `blockingString(res)` from the same package, or `stringResource(res)`
  inside composables.
- **Services**: `by inject()` / KoinComponent fields → use `AppServices.config`,
  `AppServices.bookmarkManager`, `AppServices.dialogManager` (`info.plateaukao.einkbro.AppServices`).
  `val context = LocalContext.current` keeps working (shim Context).
- **ViewModels**: `androidx.lifecycle.ViewModel` IS available (multiplatform artifact).
  Port the real ViewModel if it's mostly state + pure logic (strip Android imports,
  replace repository/DAO calls with in-memory sample data). If it's service-heavy
  (TTS engine, network), write a stub ViewModel with the same public API returning
  representative fake state — reading UIs should look "in progress", lists have
  2–3 sample entries.
- **Existing shared stubs** (do NOT recreate, do NOT edit — they are owned centrally):
  `EBToast`, `DialogManager`, `IntentUnit`, `ShareUtil`, `HelperUnit`, `BrowserUnit`,
  `BackupUnit`, `LocaleManager`, `ViewUnit`, `Album`, `BookmarkManager`, `AppServices`,
  `MarkdownParser`, `DateFormat`, `System`, `Uri`, `Locale`, `PlatformCompat`
  (LocalContext/screenWidthDp/screenHeightDp/blockingString), `DialogComposables.kt`
  (HorizontalSeparator, VerticalSeparator, ActionIcon, toScreenPoint, ComposeDialogFragment
  anchor object), shims under `android/`, `androidx/`. If one is missing a member you
  need, DO NOT edit it — write the workaround in your own file and report the gap in
  your final message.
- **New stubs you own**: put support types only your bundle needs in your ported file
  or a new file in the matching package. You own the files listed in your assignment.
- **Android-only flows** (file pickers, launchers, share sheets, printing, TTS engine
  calls): replace the action body with `EBToast.show(context, "<what would happen>")`.
- **Window/dialog chrome** (`DialogWindowProvider`, `window.setDimAmount`, gravity,
  `setBackgroundDrawableResource`): delete those lines. Compose `Dialog {}` /
  `AlertDialog` from CMP work as-is.
- **AndroidView blocks**: rewrite with the Compose equivalent (usually `Text`;
  `TextUtils.TruncateAt.MIDDLE` → `overflow = TextOverflow.Ellipsis`).
- **SimpleDateFormat** → `info.plateaukao.einkbro.util.DateFormat.format(millis, pattern)`.
- **kotlinx.coroutines / Flow / kotlinx.serialization / navigation-compose /
  sh.calvin.reorderable**: all available in commonMain — port as-is.
- **Never** delete a composable to make things compile; the point is UI coverage.
  Simplify its *data plumbing*, not its layout code.

## Step 3: definition of done (per bundle)

1. Your isolated copy compiles: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
2. Every assigned file's composables exist and render from stub data (public
   entry composable per screen/dialog documented in your report).
3. Copy ONLY your owned files back into `/Users/maoyuankao/src/einkbro-ios`, rerun
   the compile there; fix errors that involve your files.
4. Report: list of entry composables (name + required params) per ported screen,
   plus any gaps/stub-limitations worth knowing.
