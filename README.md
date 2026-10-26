# UBAHVO for Android

**UBAHVO** is a native Android app for converting supported YouTube video links into MP4 video or MP3 audio. The name is being broadened for the product's long-term direction, but **this beta is not yet a universal file converter**.

**Current status: beta (`0.1.0-beta.5`).** This branding update is not a production-stable release.

## What works today

- Native Android app built with Kotlin, Jetpack Compose, Material 3, ViewModel, and StateFlow.
- Converts supported single-video YouTube URLs to MP4 video or MP3 audio through the Android yt-dlp wrapper and FFmpeg.
- MP4 quality selection, progress reporting, cancellation, and Android share-sheet URL intake.
- Recent history for the five latest app-owned outputs, including file sizes, local timestamps, and open/share shortcuts.
- Scoped-storage publishing through MediaStore; no broad storage permission is requested.
- HTTPS YouTube URL validation and unit tests.
- User-triggered updates for the official stable yt-dlp script. The updater validates the HTTPS host, asset size, script header, and GitHub-published SHA-256 digest before installation, then verifies the staged and installed files and protects against rollback.
- CI tests and builds architecture-specific APKs, checks required native C++ runtime packaging, and supports beta prereleases.

## Get the beta

Visit the [UBAHVO GitHub Releases page](https://github.com/RidTheWann/UBAHVO-Project/releases) to get an APK and its SHA-256 checksum. Choose the APK that matches your device ABI; **arm64-v8a is the typical choice for modern Android phones**. Architecture-specific APKs avoid the larger download size of a universal APK.

The APKs are built from the Android debug variant and are intended for early testing, not as production-signed releases. The signing key is generated on the GitHub runner, so a later beta may require uninstalling a previous build before installing the update.

## Branding and compatibility

The user-facing product name and launcher mark are now UBAHVO. To preserve upgrade compatibility during this first branding step, the Android `applicationId`, Kotlin package names, and current output folder (`Downloads/YoutubeConvert`) remain unchanged. Existing files in that folder are not moved or renamed.

## Native runtime compatibility

The published `youtubedl-android` FFmpeg 0.18.1 artifact has an open upstream report because its bundled Rubber Band library needs `libc++_shared.so`, which is missing from that payload: https://github.com/yausername/youtubedl-android/issues/369. The build pins Android NDK 27.2.12479018, packages an ABI-matched C++ runtime, and stages it into FFmpeg's private `usr/lib` directory because that directory is included in the wrapper's `LD_LIBRARY_PATH`. CI verifies that APKs contain the runtime and unit tests cover the staging logic. Physical-device execution still needs validation.

The same FFmpeg dependency has an upstream 16 KB memory-page alignment issue: https://github.com/yausername/youtubedl-android/issues/365. On startup, the app checks page size and disables conversion unless it detects a verified 4 KB page size. This is a fail-closed mitigation, not an upstream binary fix. Do not treat this beta as supporting 16 KB-page devices.

## Build locally

Open the repository root in Android Studio and install Android SDK Platform 36, Build Tools 36.0.0, and Android NDK 27.2.12479018. Use JDK 17 and Gradle 8.13.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Architecture-specific APKs are produced under `app/build/outputs/apk/debug/`, for example `app-arm64-v8a-debug.apk`.

## Product roadmap

Development is being staged so each change can be verified before the next stage:

1. **Branding:** introduce the UBAHVO product identity without changing the app's package ID or existing output files.
2. **Accessible, beginner-friendly UI:** simplify the workflow and wording; make the main choices, progress, results, and errors easy to understand for children, adults, and older users.
3. **Universal conversion foundation:** design a format-aware pipeline for supported local files and media. New input types and formats will be added only after their dependencies, quality, safety, licensing, and device compatibility are verified.

See [Product roadmap](docs/PRODUCT_ROADMAP.md). Items beyond the currently working YouTube-to-MP4/MP3 workflow are plans, not present features.

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service's terms. YouTube's terms generally prohibit downloading except where the service authorizes it or the relevant rights holder has given permission. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. The ABI-matched C++ runtime comes from the pinned Android NDK. Binary dependencies are downloaded by Gradle and are not committed to this repository.
