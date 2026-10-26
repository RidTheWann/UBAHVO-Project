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

Debug APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow runs the same unit-test and debug-APK tasks. The initial repository does not yet include Gradle Wrapper scripts/JAR; use a local Gradle 8.13 installation for this bootstrap revision.
