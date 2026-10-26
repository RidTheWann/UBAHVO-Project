# Youtube Convert for Android

Native Android app built with Kotlin and Jetpack Compose. It accepts a YouTube video URL, lets the user choose MP4 video or MP3 audio, and saves the result to **Downloads/YoutubeConvert**.

**Current status: beta.** This project prioritizes transparent failure handling and responsible use. The current beta is not a production-signed stable release.

## Features

- Kotlin + Jetpack Compose, Material 3, ViewModel and StateFlow.
- yt-dlp through the Android wrapper, with FFmpeg support for audio extraction and media remuxing.
- MP4 quality selection, MP3 audio extraction, progress, cancellation, and Android share-sheet URL intake.
- Scoped-storage publishing through MediaStore. No broad storage permission is requested.
- HTTPS YouTube URL validation and unit tests.
- User-triggered update of the official stable yt-dlp binary, validating the HTTPS host, asset size, script header, and GitHub-published SHA-256 digest before atomic installation, with status and errors shown in the UI.
- CI builds architecture-specific APKs, verifies that each APK includes the required C++ runtime, and publishes beta GitHub prereleases after tests and APK assembly pass.

## Latest beta

The `0.1.0-beta.3` update hardens the manual stable yt-dlp updater with on-disk SHA-256 verification of both the staged and final installed script, plus rollback protection. Use the `Perbarui` control in the app when network access is available; the updater accepts only the official GitHub release asset and verifies its published SHA-256 digest before atomically replacing the bundled script. It does not silently replace the downloader in the background.

Use the [GitHub Releases page](https://github.com/RidTheWann/Youtube-Convert/releases) to get an APK and the SHA-256 checksum. Choose the APK matching your device ABI; **arm64-v8a is the typical choice for modern Android phones**. Architecture-specific APKs avoid the large download size of a universal APK.

The APKs are built from the Android debug variant and are intended for early testing, not as a production-signed release. Because the signing key is generated on GitHub's runner, a later beta may require uninstalling the previous build before installing the update.

## Native runtime compatibility

The published `youtubedl-android` FFmpeg 0.18.1 artifact has an open upstream report because its bundled Rubber Band library needs `libc++_shared.so`, which is missing from that payload: https://github.com/yausername/youtubedl-android/issues/369. The build pins Android NDK 27.2.12479018, packages an ABI-matched C++ runtime, and stages it into FFmpeg's private `usr/lib` directory because that directory is included in the wrapper's `LD_LIBRARY_PATH`. CI verifies the APK contains the runtime and unit tests cover the staging logic. Physical-device execution still needs validation.

The same FFmpeg dependency has an upstream 16 KB memory-page alignment issue: https://github.com/yausername/youtubedl-android/issues/365. On startup, the app checks the page size and disables conversion unless it detects a verified 4 KB page size. This is a fail-closed mitigation, not an upstream binary fix. Do not treat this beta as supporting 16 KB-page devices.

## Build locally

Open the repository root in Android Studio and install Android SDK Platform 36, Build Tools 36.0.0, and Android NDK 27.2.12479018. Use JDK 17 and Gradle 8.13.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Architecture-specific APKs are produced under `app/build/outputs/apk/debug/`, for example `app-arm64-v8a-debug.apk`.

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service's terms. YouTube's terms generally prohibit downloading except where the service authorizes it or the relevant rights holder has given permission. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. The ABI-matched C++ runtime is taken from the pinned Android NDK. Binary dependencies are downloaded by Gradle and are not committed into this repository.
