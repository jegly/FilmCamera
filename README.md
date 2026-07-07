<p align="center">
    <img src="screenshots/filmcamera_hero.png" alt="FilmCamera"/>
  </p>



**A private, offline film camera for Android — real analog film simulation with zero network access**

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0-cba6f7.svg?logo=kotlin&logoColor=cdd6f4&labelColor=1e1e2e)](https://kotlinlang.org)
  [![Android](https://img.shields.io/badge/Android-8.0%2B-a6e3a1.svg?logo=android&logoColor=cdd6f4&labelColor=1e1e2e)](https://developer.android.com)
  [![Version](https://img.shields.io/badge/Version-1.4-89b4fa.svg?labelColor=1e1e2e)](https://github.com/jegly/FilmCamera/releases)
  [![License](https://img.shields.io/badge/License-GPL%203.0-fab387.svg?labelColor=1e1e2e)](LICENSES/GPL-3.0.txt) [![Jetpack 
  Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-b4befe.svg?logo=jetpackcompose&logoColor=cdd6f4&labelColor=1e1e2e)](https://developer.android.com/jetpack/compose)
  [![CameraX](https://img.shields.io/badge/CameraX-OpenGL%20ES-f5c2e7.svg?labelColor=1e1e2e)](https://developer.android.com/training/camerax)

  [![Download APK](https://img.shields.io/badge/Download_APK-94e2d5?style=for-the-badge&logo=android&logoColor=1e1e2e)](https://github.com/jegly/FilmCamera/releases/latest)


If this project helped you, please ⭐️ star it. **Also try [rss](https://github.com/jegly/rss)**, **[OfflineLLM](https://github.com/jegly/OfflineLLM)** and **[Box](https://github.com/jegly/Box)** — privacy-first, on-device Android apps built on the same philosophy.


## Features

- **Film Simulation** — 290+ film stocks across Kodak, Fuji, Ilford, Polaroid, Agfa, Rollei and Lomography, driven by 360+ hand-tuned 3D LUTs
- **Real-time GPU Preview** — an OpenGL ES `CameraEffect` grades the live viewfinder frame-by-frame; automatically falls back to a CPU path on unsupported devices
- **Film Look on Video** — the LUT and grain are baked into recorded video, not just the preview — what you see is what you record
- **3D LUT Engine** — `.cube` and HaldCLUT formats, trilinear interpolation, running on the GPU for preview/video and on the CPU for full-resolution stills
- **Custom LUT Import** — bring your own `.cube` or HaldCLUT files; imported looks grade the preview, video **and** saved photos
- **Manual Pro Controls** — manual ISO, shutter speed and focus distance via Camera2, plus AE / exposure lock and EV compensation
- **Analog Processing** — film grain, randomised light leaks, highlight rolloff, per-stock colour grading and a configurable LED / DSEG-7 date imprint
- **Shooting Modes** — Photo, Video, and Long Exposure (multi-frame averaging for light trails and motion blur)
- **Multi-Lens** — ultra-wide / main / tele / front, with pinch-to-zoom and tap-to-focus
- **Composition Aids** — luminance histogram, horizon level, live exposure readout, and grid overlays (thirds, square, 9×9, golden ratio, diagonal)
- **Gestures** — pinch to zoom, double-tap to flip camera, swipe to change film, tap the thumbnail to review your last shot
- **RAW / DNG** capture toggle, self-timer, and screen flash for front-camera shots
- **Zero Network** — the app ships with **no `INTERNET` permission at all**; nothing can ever leave the device

## Security & Privacy

| Layer | Detail |
|-------|--------|
| Network | No `INTERNET` permission — stripped in the manifest via `tools:node="remove"`, along with `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and `NEARBY_WIFI_DEVICES`; the app is physically incapable of network I/O |
| Processing | 100% on-device — CameraX, OpenGL ES and a WorkManager CPU pipeline; no cloud, no analytics, no telemetry, no ad SDKs |
| Storage | Scoped media only (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on Android 13+); photos and video written through `MediaStore`; no broad external-storage access on modern Android |
| Sharing | `FileProvider` with `exported=false`, scoped to cache subdirectories, with per-file URI grants |
| Backup | Raw captures, processed cache and settings excluded from cloud backup and device transfer |
| Permissions | Camera + optional microphone (for video) only — no location, no contacts, no network |

## Install

1. Download the APK from [Releases](https://github.com/jegly/FilmCamera/releases/latest)
2. **Settings → Apps → Install unknown apps** → allow your file manager
3. Open the APK and tap Install

Requires Android 8.0+.

## Build from Source

```
git clone https://github.com/jegly/FilmCamera.git
cd FilmCamera
./gradlew assembleRelease
```

**Prerequisites:** JDK 17, Android SDK (compileSdk 36)

## Credits

An extensively reworked fork of [photoncam](https://github.com/cinethe-zs/photoncam) by cinethe-zs. Film LUTs are derived from open film-emulation datasets. Distributed under the same GPL-3.0 license as the upstream project.

## License

GNU General Public License v3.0

---

**[www.jegly.xyz](https://www.jegly.xyz)**

[![Buy Me A Coffee](https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png)](https://www.buymeacoffee.com/jegly)
