# UBAHVO for Android

**UBAHVO** is a native Android media converter for supported HTTPS media links from multiple sites and user-selected local audio/video files. The current output formats are MP4 video and MP3 audio. Direct downloads include a universal APK and smaller ABI-specific APKs for ARM phones and x86_64 emulators.

**Current status: beta (`0.1.0-beta.16`).** This is an early test build, not a production-stable release.

## What works today

- Native Android app built with Kotlin, Jetpack Compose, Material 3, ViewModel, and StateFlow.
- Converts supported HTTPS media links to MP4 video or MP3 audio. For images, UBAHVO has a first-party Kotlin HTTP/HTML metadata path that saves direct image URLs and the primary image exposed by public Open Graph/Twitter metadata without invoking yt-dlp or FFmpeg. Public-page metadata is not a promise of full-resolution carousel support; provider-specific image galleries remain in progress.
- Remote-link video quality options use explicit resolution caps: 8K (4320p), 4K (2160p), 1440p, 1080p, 720p, 480p, and 360p. There is no unbounded “best” option. The 8K and 4K choices show compatibility warnings because playback depends on the phone's codec/decoder support; a successful download does not guarantee smooth playback. Selection prefers MP4-compatible streams and falls back to other source containers for remuxing where possible.
- Progress reporting for extraction, format selection, transfer, and post-processing; segmented DASH/HLS downloads request up to four concurrent fragments. Initial extraction still depends on the site and network.
- Local audio/video conversion to MP3 or MP4 from files selected through Android's document picker. FFprobe inspects the streams before FFmpeg runs; selected input is copied into app-private cache, and temporary files are removed when the job finishes or is cancelled.
- Saves supported direct images and primary preview images from public metadata pages to `Download/UBAHVO`, preserving recognized JPEG, PNG, WebP, GIF, AVIF, HEIC, and BMP payloads. No external image downloader library is used for this path.
- Recent history for the five latest app-owned outputs, including file sizes, local timestamps, and open/share shortcuts.
- Scoped-storage publishing through MediaStore; no broad storage permission is requested.
- HTTPS media URL preflight and unit tests.
- An animated “Selamat Datang” launch screen followed by a startup loader while native media components initialize and eligible verified component updates are checked.
- Background checks for published UBAHVO releases using Android's JobScheduler, with system notifications when permission is granted and an in-app update notice otherwise. APK installation is always confirmed by Android.
- A small dependency-free Rust validation core, built as ABI-specific static libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, linked into a small C++17 JNI adapter. The established JVM parser remains as a fallback when the native library is unavailable or the input is non-ASCII.
- Fast-path validation rejects oversized URLs and malformed or unsupported hosts before starting the downloader; this native parser is deliberately narrow and does not replace network/media processing.
- Automatically checks and updates the official stable yt-dlp support component on its maintenance schedule. The updater validates the HTTPS host, asset size, script header, and GitHub-published SHA-256 digest before installation, then verifies staged and installed files and protects against rollback.
- CI runs Rust and Android unit tests, builds universal and ABI-specific APKs plus an unsigned AAB candidate, verifies native runtime packaging, measures download sizes, and publishes beta prereleases.

## Download UBAHVO

Open the [UBAHVO GitHub Releases page](https://github.com/RidTheWann/UBAHVO-Project/releases) and choose a build that matches your device:

- **Universal APK — easiest:** `UBAHVO-0.1.0-beta.16-universal-debug.apk` includes `arm64-v8a`, `armeabi-v7a`, and `x86_64` in one download. It is larger because it carries every supported native runtime.
- **Most current Android phones:** `UBAHVO-0.1.0-beta.16-arm64-v8a-debug.apk` is for 64-bit ARM phones.
- **Older 32-bit ARM devices:** `UBAHVO-0.1.0-beta.16-armeabi-v7a-debug.apk` is for supported 32-bit ARM phones.
- **Emulators / compatible Intel Android devices:** `UBAHVO-0.1.0-beta.16-x86_64-debug.apk` is for x86_64 Android environments.

Prefer the ABI-specific APK when you know your device architecture; it avoids downloading unrelated native libraries. Install the one matching your device. The release also publishes a `SHA256SUMS.txt` file so each APK can be verified after download.

R8 code shrinking, resource shrinking, and Indonesian/English resource filtering are enabled. The universal APK may still exceed the 100,000,000-byte target because it bundles all ABIs and Python/FFmpeg runtimes; CI reports measured sizes rather than assuming that the budget is met.

CI also builds an optimized Android App Bundle (AAB) candidate. That artifact is unsigned and is not a production or Google Play-ready release. App Bundle distribution requires an owner-controlled upload key configured through secure release signing; no signing secrets belong in this repository.

The published APKs are built from the Android debug variant and use the CI runner's debug signing key. Later beta builds can require reinstalling. Do not use these debug-signed APKs as production updates.
## Application ID and existing installations

The new application ID is `com.ridthewann.ubahvo`. The new application ID was introduced in beta.8, so UBAHVO is separate from older builds using `com.ridthewann.youtubeconvert`. Later betas keep the new ID; debug signing can still require reinstalling between CI builds, so back up important files and export folders before uninstalling any build.

New outputs are saved to `Download/UBAHVO`. Existing files in `Download/YoutubeConvert` are not moved or renamed. Recent history attempts to include both folders and both package-owner IDs where Android permits access. If a legacy file is important, keep a backup outside the app before uninstalling an older build.

## Native runtime compatibility

The Rust parser and C++ bridge are compiled for the packaged `arm64-v8a`, `armeabi-v7a`, and `x86_64` ABIs; the bridge is linked with 16 KB ELF alignment. This does **not** make the bundled third-party FFmpeg payload 16 KB-compatible; see the existing limitation below.

The published `youtubedl-android` FFmpeg 0.18.1 artifact has an open upstream report because its bundled Rubber Band library needs `libc++_shared.so`, which is missing from that payload: https://github.com/yausername/youtubedl-android/issues/369. The build pins Android NDK 27.2.12479018 and stages an ABI-matched C++ runtime into FFmpeg’s private `usr/lib` directory because that directory is included in the wrapper’s `LD_LIBRARY_PATH`. CI verifies that the APK contains the required runtime; physical-device execution still needs validation.

The same FFmpeg dependency has an upstream 16 KB memory-page alignment issue: https://github.com/yausername/youtubedl-android/issues/365. UBAHVO currently disables conversion unless it detects a verified 4 KB page size. Therefore this beta cannot yet convert on every 16 KB-page phone; the universal APK improves ABI coverage but does not fix this native dependency.

## Build locally

Open the repository root in Android Studio and install Android SDK Platform 36, Build Tools 36.0.0, CMake 3.22.1, and Android NDK 27.2.12479018. Use JDK 17, Gradle 8.13, and stable Rust. Install the Android targets used by the native build before assembling:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo fmt --manifest-path app/src/main/rust/Cargo.toml -- --check
cargo test --manifest-path app/src/main/rust/Cargo.toml --locked
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
gradle --no-daemon -Pubahvo.bundleOnly=true :app:bundleRelease
```

Debug APKs are produced under `app/build/outputs/apk/debug/` as `UBAHVO-0.1.0-beta.16-universal-debug.apk` and ABI-specific `UBAHVO-0.1.0-beta.16-{arm64-v8a,armeabi-v7a,x86_64}-debug.apk` files. CI also produces an unsigned AAB candidate under `app/build/outputs/bundle/release/app-release.aab`.

## Product roadmap

Development now prioritizes a first-party image link pipeline and provider-neutral media architecture. Public direct images and primary preview metadata can be saved without running the third-party media downloader. Site-specific carousel support, more image formats, playlists, batch jobs, and broad codec support remain future work subject to authorization, licensing, safety, and device-compatibility checks.

See [Product roadmap](docs/PRODUCT_ROADMAP.md). Local conversion currently covers audio-to-MP3 and video-to-MP4 only when the source streams and bundled encoders are compatible. For the measured save-path bottleneck, language/runtime decision, and download-size trade-offs, see [Performance audit](docs/PERFORMANCE_AUDIT.md).

## Use responsibly

Only download content that you own or are authorized to download, and comply with applicable law and the source service’s terms. The app does not implement DRM bypass, account-cookie extraction, or private/paid content access. An ownership checkbox is a user attestation, not an authorization check.

## Dependencies and licensing

This project is licensed under GPL-3.0-only. It uses the `youtubedl-android` library and its FFmpeg component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and review upstream notices before redistributing APKs. The ABI-matched C++ runtime comes from the pinned Android NDK. Binary dependencies are downloaded by Gradle and are not committed to this repository.