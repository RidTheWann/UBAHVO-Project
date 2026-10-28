# Third-party notices

This application bundles media tooling through Gradle dependencies. Review the licenses and source corresponding to the exact resolved artifacts before distributing an APK.

## yt-dlp Android wrapper

- Artifact: `io.github.junkfood02.youtubedl-android:library:0.18.1`
- Upstream: https://github.com/yausername/youtubedl-android
- License in upstream repository: GNU General Public License v3.0 (see upstream `LICENSE`).

## FFmpeg component

- Artifact: `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`
- Upstream project/release source: https://github.com/yausername/youtubedl-android
- FFmpeg and any enabled codec libraries can carry separate license and patent conditions. Check the exact build configuration and notices shipped with the resolved artifact before redistribution.
- Known upstream compatibility reports: missing `libc++_shared.so` in the internal FFmpeg payload (https://github.com/yausername/youtubedl-android/issues/369) and 16 KB page-size alignment (https://github.com/yausername/youtubedl-android/issues/365).

## Android NDK C++ runtime

- Binary: ABI-specific `libc++_shared.so` from Android NDK 27.2.12479018.
- Official documentation: https://developer.android.com/ndk/guides/cpp-support
- The LLVM Project, including libc++, is available under the Apache License 2.0 with LLVM Exceptions. See the license included with the NDK distribution and retain applicable notices when redistributing.

## Rust native core

- Source: `app/src/main/rust/`, maintained in this repository; this module currently has no third-party Cargo dependencies.
- The Rust standard library and toolchain components are governed by the applicable Rust licensing terms, including MIT and Apache-2.0 licenses. Preserve relevant notices required by the toolchain distribution for downstream redistribution.
- The native core is compiled as an ABI-specific static library and linked into `libubahvo_native.so` by the C++ JNI adapter.

## yt-dlp

- Upstream: https://github.com/yt-dlp/yt-dlp
- The application updater fetches the latest official stable release through the GitHub Releases API and verifies the API-provided SHA-256 asset digest before installing the script.
- The upstream repository and bundled/release artifacts may have different license compositions. Consult its current license and third-party notices.

This notice is not legal advice. The application source is licensed under GPL-3.0-only; third-party components remain governed by their own applicable licenses.
