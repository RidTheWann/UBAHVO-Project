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

Gradle prepares an ABI-matched `libc++_shared.so` from the pinned NDK and packages the two ARM variants into one universal APK. The app stages the runtime into FFmpeg’s private library directory at startup to mitigate upstream issue https://github.com/yausername/youtubedl-android/issues/369.

The universal beta APK is created at `app/build/outputs/apk/debug/UBAHVO-0.1.0-beta.8-universal-debug.apk`. It includes:

- `arm64-v8a` — 64-bit ARM Android phones
- `armeabi-v7a` — 32-bit ARM Android phones

x86/x86_64 emulator and Intel-device binaries are deliberately excluded to reduce download size. R8 code shrinking, resource shrinking, and compressed native-library packaging are enabled. The universal APK is still larger than a single-ABI APK because it includes both ARM variants.

The GitHub Actions CI runs unit tests, assembles the universal APK, verifies that both ARM variants include their ABI-matched C++ runtime, and uploads one UBAHVO-branded APK artifact. The release workflow publishes one universal APK and its SHA-256 checksum.

These are debug-signed development builds, not production-signed stable releases. This build uses application ID `com.ridthewann.ubahvo`; it is installed separately from older builds that used `com.ridthewann.youtubeconvert`.

The repository currently does not include Gradle Wrapper scripts/JAR; use a local Gradle 8.13 installation for this revision.