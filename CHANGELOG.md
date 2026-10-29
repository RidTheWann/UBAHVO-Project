# Changelog

## 0.1.0-beta.14

- Add local-file MP3/MP4 conversion through Android's document picker and the bundled FFprobe/FFmpeg executables.
- Inspect media streams before conversion and select an available encoder; reject missing tracks and unsupported encoder combinations with actionable Indonesian errors.
- Copy selected inputs into app-private cache, report progress when the source duration is available, allow process cancellation, clean temporary data, and publish completed outputs through the existing MediaStore path.
- Add planner tests for probe parsing, attached album-art handling, available encoders, output-format validation, fallback encoders, and progress calculations.
- Local conversion shares the app's existing page-size compatibility gate; real-device codec and cancellation testing remains required before a stable release.

## 0.1.0-beta.13

- Add `x86_64` to the Rust static-library and C++/NDK runtime build, enabling a native UBAHVO build for compatible Android emulator images and Intel Android devices.
- Build per-ABI APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, while retaining one universal APK for users who prefer a single download.
- Build an optimized Android App Bundle (AAB) candidate in CI for future Google Play distribution; keep it unsigned until a permanent owner-controlled upload key is configured securely.
- Measure universal and ABI-specific APK sizes in CI instead of assuming the universal package meets the 100,000,000-byte target.
- Expand media link support in beta.12 to supported public HTTPS URLs from multiple yt-dlp extractors. Local-file conversion is still a separate planned engine and is not claimed as available.
- Native conversion still has the documented upstream FFmpeg 16 KB memory-page compatibility restriction; adding `x86_64` does not resolve that dependency issue.

## 0.1.0-beta.12

- Expand URL preflight from YouTube-only URLs to supported HTTPS media links across multiple yt-dlp extractors while preserving single-video validation for YouTube.
- Align Kotlin fallback and Rust/C++ native URL validation, including public DNS host checks, non-standard port rejection, credential rejection, local-host/IP rejection, and oversized-input limits.
- Update the main workflow copy so users understand that support varies by site and playlists/batch downloads are not supported.
- Add Kotlin and Rust regression coverage for common multi-site URL shapes and unsafe or malformed inputs.
- This milestone expands media-link support; it does not yet claim universal local-file conversion or guaranteed support for every website.

## 0.1.0-beta.11

- Add an optional Rust-based YouTube URL preflight, compiled for both supported ARM ABIs and linked through a minimal C++ JNI adapter.
- Preserve JVM URL validation as a fallback if the native library is unavailable or the input contains non-ASCII characters.
- Add Rust formatting/unit-test gates and native-library checks to Android CI and beta-release builds.
- Avoid reinitializing yt-dlp/FFmpeg for every Save tap; the app initializes them once per process, and Android process recreation repeats startup initialization as needed.
- Keep the existing beta.10 download-path improvements, yt-dlp cache, automatic stable updater, and minified-APK reflection safeguards.
- The native module has not yet demonstrated an end-to-end speedup; CI measures the final universal APK size, and physical-device smoke testing remains required before claiming release readiness.
All notable user-facing changes to UBAHVO are documented here.


## 0.1.0-beta.10 — 2026-10-27

- Removed the separate metadata-only yt-dlp invocation before downloads; the title now comes from the active download process, reducing duplicated link extraction and time-to-first-progress.
- Enabled yt-dlp's persistent cache in the app cache directory and made progress UI updates less frequent.
- Added automatic yt-dlp support checks when the app opens if the scheduled check is due: daily after success and no sooner than six hours after a failure. The Save action remains available during automatic checks.
- Restricted bundled resource locales to Indonesian and English and removed unused Android UI test/debug tooling dependencies to reduce the universal APK footprint.
- Fixed the CI DEX check to recognize the public reflective constructor after minification.
- Added regression tests for download-title parsing and automatic update scheduling.

## 0.1.0-beta.9 — 2026-10-26

- Added R8 keep rules for the yt-dlp/FFmpeg wrappers and Apache Commons Compress classes instantiated through reflection by `ExtraFieldUtils`.
- Hardened startup so JVM native-linkage or media-engine initialization failures disable conversion without immediately closing the app.
- Added regression tests for unsupported memory page sizes and media-engine startup failures.
- Displayed the last initialization stage in the disabled-engine message, making device-specific startup diagnosis more precise.
- Bumped Android versionCode to 9; physical-device smoke testing is still required.


## 0.1.0-beta.8 — 2026-10-26

- Changed the Android application ID and Kotlin namespace/package names to `com.ridthewann.ubahvo`.
- Replaced four architecture-specific download files with one universal ARM APK containing arm64-v8a and armeabi-v7a.
- Enabled R8 code shrinking and Android resource shrinking for the beta APK; retained compressed native-library packaging to reduce download size.
- Saved new exports in Download/UBAHVO and retained legacy-path/legacy-owner support for recent history where Android permits access.
- Documented that beta.8 installs as a separate app from previous `com.ridthewann.youtubeconvert` builds.
- Kept the 16 KB memory-page guard; conversion remains disabled on page sizes not verified as 4 KB.

## 0.1.0-beta.7 — 2026-10-26

- Renamed local debug APK files, CI artifacts, GitHub prerelease assets, release notes, and checksum patterns to UBAHVO.
- Renamed the Android application class, Compose theme function, and XML theme resource to match the product name.
- Saved new exports and temporary work files under UBAHVO-branded directories while retaining history access to legacy output paths.
- Kept the current YouTube URL → MP4/MP3 feature scope; universal local-file conversion is a later stage.

## 0.1.0-beta.6 — 2026-10-26

- Reworked main workflow wording into clear, numbered steps and simplified the format, link, permission, progress, and output labels.
- Made the main action reflect the selected result: save video or save audio.
- Replaced user-facing downloader/initialization errors with actionable plain-language guidance.
- Allowed choice cards and the main action to grow more naturally with larger system text.
- Kept conversion, updater verification, native compatibility guards, storage behavior, and app identity unchanged.

## 0.1.0-beta.5 — 2026-10-26

- Introduced the UBAHVO product name, in-app header branding, developer credit, and a non-YouTube-specific launcher mark.
- Added a phased product roadmap and made the current YouTube URL → MP4/MP3 scope explicit.

## 0.1.0-beta.4 — 2026-10-26

- Added a recent-conversion history for the five latest app-owned files in Downloads/YoutubeConvert.
- Added quick open/share actions plus file sizes and local timestamps.
- Limited history to app-owned MediaStore entries without requesting broad storage permissions.
- Improved MediaStore output stream cleanup and added formatting unit tests.

## 0.1.0-beta.3 — 2026-10-26

- Verified the yt-dlp staging file and final installed file against the official release size and SHA-256 digest directly from disk.
- Added rollback protection so a failed final-file verification restores the previously installed downloader.
- Added regression coverage for same-length tampering of a persisted updater file.

## 0.1.0-beta.2 — 2026-10-26

- Added a manual updater for the official stable yt-dlp binary, so users can refresh site compatibility without waiting for a full APK update.
- Added clear update progress, version status, and network/update failure messages.
- Prevented conversion and format changes while the updater is running, and locked format selection during conversion.
- Incremented Android versionCode for beta upgrade progression.
- Kept startup compatibility fail-closed on devices whose memory page size is not verified for the bundled native FFmpeg runtime.

## 0.1.0-beta.1 — 2026-10-26

- Initial native Kotlin/Compose beta with MP4 and MP3 output.
- Added HTTPS URL validation, cancellation, scoped-storage output publishing, and architecture-specific APKs.
- Added CI tests, ABI runtime packaging checks, SHA-256 checksum generation, and GitHub prerelease automation.
- Added compatibility mitigations for known upstream FFmpeg native-runtime issues.
