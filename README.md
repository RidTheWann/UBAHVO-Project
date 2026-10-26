# Youtube Convert for Android

Native Android app built with Kotlin and Jetpack Compose. It accepts a YouTube video URL, lets the user choose MP4 video or MP3 audio, and saves the result to **Downloads/YoutubeConvert**.

## Current implementation

- Kotlin + Jetpack Compose, Material 3, ViewModel and StateFlow.
- yt-dlp through the Android wrapper, with FFmpeg support for audio extraction and media remuxing.
- MP4 quality selection, MP3 audio extraction, live progress, cancellation and Android share-sheet URL intake.
- Scoped-storage publishing through MediaStore. No broad storage permission is requested.
- Unit tests for URL validation and a GitHub Actions build workflow.

## Build

Open the repository root in Android Studio, install Android SDK Platform 36, and use JDK 17 with Gradle 8.13. The project currently builds via an installed Gradle distribution; CI installs Gradle directly.

On a machine with Gradle 8.13 available:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service's terms. YouTube's terms generally prohibit downloading except where the service authorizes it or the relevant rights holder has given permission. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. Binary dependencies are downloaded by Gradle and are not committed into this repository.
