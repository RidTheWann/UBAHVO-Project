# Youtube Convert for Android

Native Android app built with Kotlin and Jetpack Compose. It accepts a YouTube video URL, lets the user choose MP4 video or MP3 audio, and saves the result to **Downloads/YoutubeConvert**.

**Current status: beta.** This project prioritizes transparent failure handling and responsible use. The current beta is not a production-signed stable release.

## Features

- Kotlin + Jetpack Compose, Material 3, ViewModel and StateFlow.
- yt-dlp through the Android wrapper, with FFmpeg support for audio extraction and media remuxing.
- MP4 quality selection, MP3 audio extraction, progress, cancellation, and Android share-sheet URL intake.
- Scoped-storage publishing through MediaStore. No broad storage permission is requested.
- HTTPS YouTube URL validation and unit tests.
- GitHub Actions checks tests and assembles a debug APK; the main branch is configured to publish a beta GitHub prerelease when a new app version is pushed.

## Latest beta

Use the [GitHub Releases page](https://github.com/RidTheWann/Youtube-Convert/releases) to get the APK and SHA-256 checksum. The release APK is built from the Android debug variant and is intended for early testing, not as a production-signed release. Because the signing key is generated on GitHub's runner, a later beta may require uninstalling the previous build before installing the update.

## Compatibility notice

The currently pinned `youtubedl-android` FFmpeg dependency has an upstream report about 16 KB memory-page compatibility in its bundled FFmpeg payload: https://github.com/yausername/youtubedl-android/issues/365. On startup, the app checks the device page size and conservatively disables conversion unless it detects a verified 4 KB page size. This avoids invoking a native dependency known to crash on some 16 KB page-size devices. Do not treat the current beta as supporting 16 KB-page devices.

## Build locally

Open the repository root in Android Studio, install Android SDK Platform 36, and use JDK 17 with Gradle 8.13.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service's terms. YouTube's terms generally prohibit downloading except where the service authorizes it or the relevant rights holder has given permission. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. Binary dependencies are downloaded by Gradle and are not committed into this repository.
