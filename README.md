# PhotonCam

An Android film camera emulator. Select a film stock, shoot through a vintage rangefinder viewfinder, and get processed images with authentic analog characteristics — grain, light leaks, date imprint, and per-film colour grading via 3D LUTs.

## Features

- **36 film stocks** across Kodak, Fuji, Ilford, and Lomography — each with a unique 3D LUT, grain profile, and colour character
- **Rangefinder UI** — physical camera body aesthetic with D-pad EV control, lens selector, and shutter button
- **Flash** — hardware flash on back camera; screen flash (full white, max brightness) on front camera
- **Grain & light leaks** — procedural per-shot analog artefacts
- **Date imprint** — configurable 1990s-style timestamp: format, colour, font (LED/7-segment), size, position, glow
- **Favorites** — star any film stock, filter to favorites in the selector
- **Persistent roll counter** — cumulative shot count since app install, plus a per-roll frame counter (36 exp.)
- **Lens selector** — ultrawide / main / telephoto / front camera switching via D-pad or tap
- **VIEW / SHARE** — always available if any PhotonCam photo exists in the gallery

## Film stocks

| Brand | Stock | Character |
|---|---|---|
| Kodak | Portra 400 | Warm, pastel, lifted shadows |
| Kodak | Ektar 100 | Hyper-saturated, ultra-fine grain |
| Kodak | Gold 200 | Golden warmth, holiday snapshots |
| Fuji | Provia 100F | Neutral slide film, faithful colour |
| Fuji | Velvia 50 | Extreme saturation, electric greens |
| Fuji | Superia 400 | Cool shadows, everyday versatility |
| Ilford | HP5 Plus | Classic reportage B&W |
| Ilford | Delta 3200 | Extreme grain, dramatic shadows |
| Ilford | FP4 Plus | Fine grain, wide tonal latitude |
| Lomography | LomoChrome Purple | Greens shift to purple |
| Lomography | Cross Process | C-41 in E-6 chemistry |

## Date imprint

Optionally burn a date stamp into each photo in the style of 1990s disposable cameras. Configurable: format, colour, font (including LED/7-segment), size, position, and glow intensity.

## Build

```bash
./gradlew assembleDebug       # debug APK → app/build/outputs/apk/debug/photoncam-v1.0.0.apk
./gradlew installDebug        # install on device / emulator
```

Requires Android SDK, min API 26 (Android 8.0).

## Third-party credits

- **DSEG7 font** — 7-segment LED display typeface used for the date imprint LED style.
  © 2020 Keshikan — [github.com/keshikan/DSEG](https://github.com/keshikan/DSEG)
  Licensed under [SIL Open Font License 1.1](https://scripts.sil.org/OFL).
