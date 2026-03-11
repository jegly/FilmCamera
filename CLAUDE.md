# PhotonCam — Android Film Camera Emulator

## Project Overview

PhotonCam is an Android app that emulates analog film cameras. Users select a film stock (Kodak, Fuji, Ilford, Lomography), take photos through a live viewfinder styled like a vintage rangefinder camera, and receive processed images with authentic film characteristics.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM (ViewModel + StateFlow) |
| Camera | CameraX |
| Image processing | RenderScript / AGSL shaders + OpenGL ES |
| DI | Hilt |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Build | Gradle with Kotlin DSL |

## Project Structure

```
app/
  src/main/
    java/com/photoncam/
      camera/          # CameraX setup, capture, preview
      film/            # Film stock definitions and LUT/shader logic
      processing/      # Image pipeline: grain, light leaks, date imprint
      ui/
        viewfinder/    # Main camera screen (rangefinder UI)
        filmselect/    # Film stock picker
        preview/       # Post-capture review screen
        theme/         # Design tokens, colors, typography
      utils/           # Gallery save, share, permissions
    res/
      raw/             # LUT .cube files per film stock
      drawable/        # Camera body assets, textures
```

## Film Stocks

Each film stock is a `FilmStock` data class with:
- Color grading via 3D LUT (.cube file)
- Grain amount + size
- Highlight/shadow rolloff curve
- Color temperature shift
- Light leak probability and style

### Included Stocks

**Kodak:** Portra 400, Ektar 100, Gold 200
**Fuji:** Provia 100F, Velvia 50, Superia 400
**Ilford:** HP5 Plus (B&W), Delta 3200 (B&W), FP4 Plus (B&W)
**Lomography:** LomoChrome Purple, cross-process (XPro)

## UI — Vintage Rangefinder Style

- **Viewfinder screen**: large live preview (full screen), metal/leather texture overlay at top and bottom, film stock name displayed like a camera top plate, frame counter (36 shots per roll), shutter button styled as physical release
- **Film selector**: horizontal scroll of film canisters with brand color coding
- **Post-capture preview**: polaroid-style reveal animation, share/save actions

## Features

- **Grain & light leaks**: randomized per-shot analog artifacts using procedural GLSL noise
- **Date imprint**: optional orange timestamp burned into bottom-right corner (disposable camera style), toggleable in settings
- **Share / save to gallery**: processed photo saved to `Pictures/PhotonCam/` via MediaStore, share sheet available immediately after capture
- **Roll simulation**: 36 exposures per roll; frame counter visible on viewfinder; roll "finished" state shows rewind animation

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device/emulator
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Lint checks
```

Requires a physical device or emulator with camera support (API 26+).

## Key Conventions

- All UI in Jetpack Compose; no XML layouts
- `ViewModel` never imports Android framework except via Hilt-injected use cases
- Image processing runs off the main thread (Dispatchers.Default / CameraX executor)
- Film LUT files live in `res/raw/` named `lut_<brand>_<name>.cube`
- Use `Result<T>` for all camera and IO operations — no unchecked exceptions
- Permissions (CAMERA, WRITE_EXTERNAL_STORAGE) requested at runtime with Accompanist
