# Building locally

## Requirements

- Android Studio with Android SDK Platform 36 and Build Tools 36.0.0
- Android NDK 27.2.12479018 (pinned in app/build.gradle.kts)
- JDK 17
- Gradle 8.13
- Network access to Google Maven and Maven Central to resolve dependencies

## Build and test

From the repository root:

```powershell
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Gradle prepares an ABI-matched `libc++_shared.so` from the pinned NDK for every ABI, packages it into the APK, and the app stages it into FFmpeg's private library directory at startup to mitigate upstream issue https://github.com/yausername/youtubedl-android/issues/369.

The build creates ABI-specific APKs under `app/build/outputs/apk/debug/`:

- `app-arm64-v8a-debug.apk` — most modern Android phones
- `app-armeabi-v7a-debug.apk` — 32-bit ARM devices
- `app-x86_64-debug.apk` — x86_64 devices/emulators
- `app-x86-debug.apk` — 32-bit x86 devices/emulators

Install only the APK matching the target device ABI. These are debug-signed development builds, not production-signed stable releases.

The GitHub Actions CI tests the source, assembles ABI-specific APKs, and verifies that each APK includes its ABI-matched C++ runtime. The beta release workflow also publishes APKs and a SHA-256 checksum after those gates pass. The repository currently does not include Gradle Wrapper scripts/JAR; use a local Gradle 8.13 installation for this revision.
