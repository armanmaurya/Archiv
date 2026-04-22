# Copilot Instructions for Archiv

## Build, test, and lint
Run from repository root (`Archiv`).

- Build debug APK: `.\gradlew.bat app:assembleDebug`
- Full module build (compile + unit tests + checks): `.\gradlew.bat app:build`
- Lint (debug): `.\gradlew.bat app:lintDebug`
- Run all JVM unit tests: `.\gradlew.bat app:testDebugUnitTest`
- Run one JVM unit test: `.\gradlew.bat app:testDebugUnitTest --tests "com.armanmaurya.archiv.ExampleUnitTest.addition_isCorrect"`
- Run all instrumentation tests (device/emulator required): `.\gradlew.bat app:connectedDebugAndroidTest`
- Run one instrumentation test (device/emulator required): `.\gradlew.bat app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.armanmaurya.archiv.ExampleInstrumentedTest#useAppContext`

## High-level architecture
- Single-module Android app (`:app`) using Jetpack Compose + Navigation Compose. `MainActivity` initializes OpenCV and hosts `AppNavHost`.
- Navigation starts on `documents`, then enters a nested `scanner_flow` graph (`camera` -> `editor/{startIndex}`), with `ScannerViewModel` shared between camera and editor destinations via a `NavBackStackEntry.sharedViewModel` helper.
- Scanner pipeline:
  - `CameraPreview` (CameraX) captures images and optionally runs real-time edge detection.
  - `FrameProcessor` (OpenCV) converts camera frames, detects document quadrilateral corners, and returns normalized coordinates.
  - Captured/imported pages are represented in `ScannerViewModel` as `PageState(uri, cropBounds, rotation, filter)`.
- Editor pipeline:
  - `EditorScreen` manipulates per-page crop/rotation/filter state.
  - Bitmap work uses helpers in `bitmap/` (`decodeSampledBitmap`, `rotateBitmapQuarterTurns`, `applyBitmapFilter`, `warpBitmapWithQuad`) plus `BitmapCache`.
  - Saving renders each page to a `PdfDocument`, then persists bytes through `ScannerPdfStorage` -> `PdfStorage`.
- Document management pipeline:
  - `PdfStorage` owns filesystem and Downloads export logic (MediaStore on Android Q+, legacy Downloads path below Q).
  - `DocumentRepository` maps stored PDF files to `Document` models and handles share/export/delete.
  - `DocumentViewModel` + `DocumentListScreen` provide list, share, export, and delete UI flow.

## Key repository conventions
- **Document IDs are filenames**: repository/storage methods pass around `documentId` as the exact PDF filename (`Document.id = file.name`), not a generated UUID.
- **Path traversal guard for document access**: `PdfStorage.resolveAppPdfFile` rejects IDs containing `/` or `\` before resolving files.
- **Normalized 4-corner geometry**: crop bounds are stored as four normalized `PointF` values (`0f..1f`) and are expected in top-left, top-right, bottom-right, bottom-left order (`orderCorners`, `sanitizeInitialBounds`).
- **Shared scanner state across screens**: scanner and editor intentionally use the same `ScannerViewModel` instance scoped to `scanner_flow`, so edits and capture state persist during navigation within that graph.
- **Manual bitmap lifecycle handling**: bitmap transformation paths explicitly recycle intermediate bitmaps in `ScannerViewModel.renderPageForPdf`; maintain this pattern when adding image-processing steps.
- **UI one-shot events are nullable state fields**: transient events/messages are exposed as nullable `mutableStateOf` values and consumed/reset (`consumeSavedDocumentEvent`, `consumeErrorMessage`, `consumeInfoMessage`).
- **Storage classes always use application context**: repository/storage wrappers capture `context.applicationContext` to avoid activity/compose context leaks.
