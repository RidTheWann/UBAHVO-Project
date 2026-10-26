# Building locally

## Requirements

- Android Studio with Android SDK Platform 36 and Build Tools 36.0.0
- JDK 17
- Gradle 8.13
- Network access to Google Maven and Maven Central to resolve dependencies

## Build and test

From the repository root:

```powershell
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The build creates ABI-specific APKs under `app/build/outputs/apk/debug/`:

- `app-arm64-v8a-debug.apk` — most modern Android phones
- `app-armeabi-v7a-debug.apk` — 32-bit ARM devices
- `app-x86_64-debug.apk` — x86_64 devices/emulators
- `app-x86-debug.apk` — 32-bit x86 devices/emulators

Install only the APK matching the target device ABI. These are debug-signed development builds, not production-signed stable releases.

The GitHub Actions CI tests the source and assembles ABI-specific APKs. The beta release workflow also publishes APKs and a SHA-256 checksum after those gates pass. The repository currently does not include Gradle Wrapper scripts/JAR; use a local Gradle 8.13 installation for this revision.
