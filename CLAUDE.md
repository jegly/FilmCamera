# PhotonCam — Android Film Camera Emulator

## Project Overview

PhotonCam is an Android app that emulates analog film cameras. Users select a film stock (Kodak, Fuji, Ilford, Lomography, CineStill, Agfa, Rollei, Polaroid), take photos through a live viewfinder styled like a vintage rangefinder camera, and receive processed images with authentic film characteristics.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM (ViewModel + StateFlow) |
| Camera | CameraX |
| Image processing | Android Bitmap API (Canvas, BitmapFactory, BitmapRegionDecoder) |
| DI | Hilt |
| Persistence | DataStore (Preferences) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Build | Gradle with Kotlin DSL |

## Project Structure

```
app/
  src/main/
    java/com/photoncam/
      camera/          # CameraX setup, capture, lens discovery (CameraManager, LensInfo)
      film/            # Film stock definitions (FilmStock, FilmCatalog, FilmBrand)
      processing/      # Image pipeline: LUT, grain, light leaks, date imprint
      ui/
        viewfinder/    # Main camera screen — rangefinder UI (ViewfinderScreen, ViewfinderViewModel)
        filmselect/    # Film stock picker sheet (FilmSelectorSheet)
        theme/         # App theme (Theme.kt)
      utils/           # Gallery save/share (GalleryExporter), settings persistence (SettingsRepository)
      MainActivity.kt
      PhotonCamApp.kt  # Hilt application class
    assets/
      fonts/           # dseg7.ttf — 7-segment LED font for date imprint
    res/
      raw/             # Hald CLUT PNGs (hald_*.png) and legacy LUT .cube files per film stock
```

## Film Stocks

Each film stock is a `FilmStock` data class with:
- Color grading via Hald CLUT PNG or `.cube` LUT (`lutResId` + `LutType`: PNG / CUBE / PRESET)
- Grain amount + grain size
- Highlight/shadow rolloff curve
- Saturation + contrast multipliers
- Shadow lift (milky/lifted look)
- Color temperature shift
- Light leak probability
- `FilmCategory`: COLOR, BLACK_AND_WHITE, SLIDE, CROSS_PROCESS, INSTANT, INFRARED
- `accentColor` for UI + `description` shown in film selector

### Brands

`FilmBrand`: KODAK, FUJI, ILFORD, LOMOGRAPHY, CINESTILL, AGFA, ROLLEI, POLAROID

The catalog (`FilmCatalog.all`) contains 36+ stocks sorted by ISO, spanning all brands and categories.

## Image Processing Pipeline

`ImageProcessor.process()` runs on `Dispatchers.Default` in this order:

1. **EXIF rotation** — physically rotate pixels to match device orientation
2. **Date imprint** — burned onto raw pixels before film processing (so the LUT ages the timestamp)
3. **Color grade** — Hald CLUT / `.cube` LUT via `LutProcessor` (LRU-cached); B&W stocks desaturate first
4. **Saturation + contrast + shadow lift** — parametric per-film adjustments (in-place pixel array)
5. **Highlight rolloff** — compresses highlights, in-place
6. **Grain** — `GrainProcessor.applyGrain()`, in-place
7. **Light leak** — probabilistic; Canvas gradient SCREEN-blended overlay
8. **JPEG save** — 95% quality to `cacheDir/processed/`

`LutProcessor` supports both HaldCLUT PNG (auto-detected) and `.cube` text format (32³/64³), with a 3-entry LRU cache.

## UI — Vintage Rangefinder Style

- **Viewfinder screen**: 2-pane layout (preview + controls). Controls panel: film name, frame counter, EV display, lens selector ring (D-pad), flash mini-button, shutter button. Live preview uses `PreviewView`.
- **Film selector sheet**: bottom sheet with favorites star toggle, category filter, brand filter, search. Tap to select.
- **Post-capture**: processed photo saved to gallery; VIEW / SHARE buttons always visible if any PhotonCam photo exists.

## Features

- **Grain & light leaks**: randomized per-shot analog artifacts
- **Date imprint**: configurable 1990s-style timestamp — style, colour (AMBER/RED/GREEN/WHITE/BLUE), font (LED/7-segment via DSEG7 / CLASSIC monospace), size, position, glow/blur
- **Flash**: hardware flash (`FLASH_MODE_ON`) for back camera; full-screen white overlay + max brightness for front camera (screen flash). Toggle persisted to DataStore.
- **Favorites**: star any film stock; filter to favorites in the selector
- **Persistent shot counter**: cumulative total shots since install + per-roll frame counter (36 exp.); both persisted via DataStore
- **Lens selector**: ultrawide / main / telephoto via zoom ratio on logical back camera; front camera via full rebind. Discovered at runtime from `ZoomState` + Camera2 focal lengths.
- **EV compensation**: D-pad up/down adjusts exposure index; persisted
- **Share / save to gallery**: `GalleryExporter` saves to `Pictures/PhotonCam/` via MediaStore; share sheet available after capture
- **VIEW button**: always shown on viewfinder if any PhotonCam photo exists in the gallery

## Settings Persistence

`SettingsRepository` (DataStore) stores `AppSettings`:
- `filmId`, `selectedLensId`, `evIndex`
- `lightLeakEnabled`, `flashEnabled`
- `dateImprintEnabled` + all date imprint style fields
- `totalShotsTaken`, `favoriteFilmIds`

All settings are loaded on init and saved after every change via `persistSettings()` in `ViewfinderViewModel`.

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK → app/build/outputs/apk/debug/photoncam-v1.0.0.apk
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Lint checks
```

Requires a physical device or emulator with camera support (API 26+).

## Key Conventions

- All UI in Jetpack Compose; no XML layouts
- `ViewModel` exposes a single `ViewfinderUiState` data class via `StateFlow`
- Image processing runs off the main thread (`Dispatchers.Default`)
- Hald CLUT PNGs: `res/raw/hald_<brand>_<name>.png`; legacy cube LUTs: `res/raw/lut_<brand>_<name>.cube`
- `LutType.PRESET` = parametric-only stock (no LUT file)
- Use `Result<T>` for all camera and IO operations — no unchecked exceptions
- Camera and IO errors surface via `uiState.errorMessage: String?`
- Permissions (CAMERA) requested at runtime with Accompanist
- `OrientationEventListener` keeps `ImageCapture.targetRotation` in sync so EXIF orientation is correct in all device orientations
- Flash mode must be re-applied after every `bindToLifecycle()` (new `ImageCapture` instance resets it)
- Zoom re-applied on every `ON_RESUME` via `DisposableEffect` + `LifecycleEventObserver` to fix CameraX not restoring zoom on app-resume
