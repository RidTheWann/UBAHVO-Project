# UBAHVO for Android

**UBAHVO** is a native Android app for converting supported single-video YouTube links into MP4 video or MP3 audio. The universal APK covers 32-bit and 64-bit ARM phones; general local-file conversion is planned for a later stage.

**Current status: beta (`0.1.0-beta.10`).** This is an early test build, not a production-stable release.

## What works today

- Native Android app built with Kotlin, Jetpack Compose, Material 3, ViewModel, and StateFlow.
- Converts supported single-video YouTube URLs to MP4 video or MP3 audio through the Android yt-dlp wrapper and FFmpeg.
- MP4 quality selection, progress reporting, cancellation, and Android share-sheet URL intake.
- Recent history for the five latest app-owned outputs, including file sizes, local timestamps, and open/share shortcuts.
- Scoped-storage publishing through MediaStore; no broad storage permission is requested.
- HTTPS YouTube URL validation and unit tests.
- Automatic background updates for the official stable yt-dlp script, plus a clear manual “Cek sekarang” action. The updater validates the HTTPS host, asset size, script header, and GitHub-published SHA-256 digest before installation, then verifies the staged and installed files and protects against rollback.
- CI tests, builds the universal ARM APK, checks native C++ runtime packaging, and publishes beta prereleases.

## Download one universal APK

Visit the [UBAHVO GitHub Releases page](https://github.com/RidTheWann/UBAHVO-Project/releases) and download the single universal APK plus its SHA-256 checksum. The APK includes both `arm64-v8a` and `armeabi-v7a`, covering 64-bit and 32-bit ARM Android phones on Android 10 (API 29) or later. x86/x86_64 emulators and Intel Android devices are excluded.

Download filename: `UBAHVO-0.1.0-beta.10-universal-debug.apk`.

R8 code shrinking, resource shrinking, and Indonesian/English resource filtering are enabled. Unused Android UI test/debug tooling is excluded from the downloadable APK. A universal APK still includes both ARM variants and the bundled Python/FFmpeg runtimes, so its download size is inherently higher than a single-ABI package.

The APK is built from the Android debug variant and is intended for early testing, not as a production-signed release. The signing key is generated on the GitHub runner, so later betas can require reinstalling.

## Application ID and existing installations

The new application ID is `com.ridthewann.ubahvo`. The new application ID was introduced in beta.8, so UBAHVO is separate from older builds using `com.ridthewann.youtubeconvert`. Beta.9 keeps the new ID; debug signing can still require reinstalling between CI builds, so back up important files and export folders before uninstalling any build.

New outputs are saved to `Download/UBAHVO`. Existing files in `Download/YoutubeConvert` are not moved or renamed. Recent history attempts to include both folders and both package-owner IDs where Android permits access. If a legacy file is important, keep a backup outside the app before uninstalling an older build.

## Native runtime compatibility

The published `youtubedl-android` FFmpeg 0.18.1 artifact has an open upstream report because its bundled Rubber Band library needs `libc++_shared.so`, which is missing from that payload: https://github.com/yausername/youtubedl-android/issues/369. The build pins Android NDK 27.2.12479018 and stages an ABI-matched C++ runtime into FFmpeg’s private `usr/lib` directory because that directory is included in the wrapper’s `LD_LIBRARY_PATH`. CI verifies that the APK contains the required runtime; physical-device execution still needs validation.

The same FFmpeg dependency has an upstream 16 KB memory-page alignment issue: https://github.com/yausername/youtubedl-android/issues/365. UBAHVO currently disables conversion unless it detects a verified 4 KB page size. Therefore this beta cannot yet convert on every 16 KB-page phone; the universal APK improves ABI coverage but does not fix this native dependency.

## Build locally

Open the repository root in Android Studio and install Android SDK Platform 36, Build Tools 36.0.0, and Android NDK 27.2.12479018. Use JDK 17 and Gradle 8.13.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The one universal APK is produced under `app/build/outputs/apk/debug/` as `UBAHVO-0.1.0-beta.10-universal-debug.apk`.

## Product roadmap

Development is staged: branding and build artifact naming first, beginner-friendly UI next, then universal local-file conversion after each new format has verified dependencies, licensing, safety, and device compatibility.

See [Product roadmap](docs/PRODUCT_ROADMAP.md). Items beyond the currently working YouTube-to-MP4/MP3 workflow are plans, not present features.

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service’s terms. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. The ABI-matched C++ runtime comes from the pinned Android NDK. Binary dependencies are downloaded by Gradle and are not committed to this repository.