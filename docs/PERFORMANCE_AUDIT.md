# UBAHVO performance and download-size audit

Audit date: 2026-10-27
Scope: link-paste-to-save latency, native-language suitability, automatic link-support maintenance, and APK download footprint.

## Confirmed findings before optimization

1. The save path called `DownloadRepository.getInfo()`, which runs `yt-dlp --dump-json`, then called `DownloadRepository.download()`, which starts yt-dlp again for the same URL. This duplicated the first network extraction and Python process startup before the actual download.
2. The upstream `youtubedl-android` wrapper adds `--no-cache-dir` unless an explicit `--cache-dir` is supplied. That prevents reuse of yt-dlp's persistent extractor cache between requests.
3. The conversion pipeline is dominated by network extraction, subprocess startup, media download, and FFmpeg post-processing. The application Kotlin code is not performing a CPU-heavy media transform that would benefit from moving it to Rust or another language.
4. The universal beta.8 APK was measured through GitHub Release metadata at 107,183,163 bytes. After the beta.10 resource/dependency trimming, CI measured the new universal APK at 106,692,084 bytes: a reduction of 491,079 bytes, but still 6,692,084 bytes above the 100,000,000-byte target. It includes both ARM ABIs and bundled Python/FFmpeg runtime payloads. The CI log shows `libpython.zip.so` and `libffmpeg.zip.so` are archive payloads that `llvm-strip` does not recognize as ELF shared objects; blindly stripping them is not a valid size optimization.
5. Previous Android CI ran unit tests and assembled the APK successfully, then failed in the post-build DEX verification script due to an over-escaped grep pattern. That verification pattern has been corrected to match the disassembly form.

## Selected changes

- Remove the metadata-only `getInfo()` invocation from the save path. The same yt-dlp run now emits its title using `--print before_dl:UBAHVO_TITLE:%(title)s`; the UI can show a title without a second extraction.
- Explicitly enable yt-dlp progress output and a cache directory under Android's app cache directory. The cache is expendable and does not pollute Downloads.
- Throttle UI state updates to meaningful progress, ETA, title, and diagnostic changes rather than recomposing the screen for every unchanged subprocess output line.
- When the app opens, check for a stable yt-dlp support update in the background if the scheduled check is due. After success, check again after 24 hours; after failure, retry on a later app launch no sooner than six hours later. The Save action stays enabled during automatic checks, and the existing verified atomic installer protects the installed script from partial writes. A clearly labelled manual action remains available.
- Restrict Android dependency resources to Indonesian and English, and remove Android UI-testing/preview dependencies that are not used by this repository's current test suite.
- Add tests for title marker parsing and the automatic-update schedule.
- Report the actual universal APK size in CI. The 100,000,000-byte target is a measured budget, not an assumed result.

## Native-language scope and performance rationale

The measured save-path bottleneck was repeated yt-dlp extraction/process startup, not a CPU-bound operation in UBAHVO-owned Kotlin code. The main performance changes are the Kotlin pipeline improvements introduced in beta.10: remove the metadata-only extraction, reuse yt-dlp's cache, reduce unnecessary UI state updates, and avoid reinitializing yt-dlp/FFmpeg on every Save tap. The application gate prevents starting conversion when startup initialization failed; Android process recreation reruns Application.onCreate, so per-job engine reinitialization is redundant. Those changes target the actual work the audit found.

Beta.11 adds a deliberately small Rust validator behind a C++17 JNI adapter to meet the project's native-core requirement. Beta.12 extends that preflight to supported public HTTPS media URLs across yt-dlp extractors while retaining YouTube single-video checks; it does **not** perform media decoding, conversion, networking, or source-service extraction. The module has no third-party Cargo dependencies and preserves the established JVM parser as a fallback.

This is not evidence that the native parser makes the overall app measurably faster. JNI setup and the added native runtime may add overhead and APK bytes for a path that is not the dominant bottleneck. CI measures the resulting APK size; an on-device benchmark must compare time from tapping **Simpan** to first visible progress before claiming an end-to-end speedup. Do not move FFmpeg work into new Rust/C++ code unless profiling identifies a real workload that the existing FFmpeg engine cannot already handle efficiently.

## Verification and remaining limits

- Unit tests cover the new parser and scheduling policy.
- CI checks the minified ZIP extra-field constructor used by Apache Commons Compress reflection, confirms both ARM ABIs and C++ runtimes exist, and reports the final APK byte count.
- CI cannot prove end-to-end improvement in real-site media extraction latency. Physical-device smoke testing should compare time from tapping **Simpan** to the first visible download progress, then test one authorized MP3 and one authorized MP4.
- The measured beta.10 universal APK is 106,692,084 bytes, so the 100 MB target is not met. Kotlin/R8/resource trimming removed 491,079 bytes from the prior release, but the dominant bundled Python and FFmpeg payloads are architecture-specific native archives. Reaching the target may require ABI-specific packages or a larger on-demand-runtime redesign; neither should be implemented blindly because both trade away the current one-universal-APK convenience and affect first-use behavior.

## Distribution follow-up (beta.13)

The beta.11 universal APK was measured at 106,990,064 bytes. Beta.13 now builds three ABI variants (`arm64-v8a`, `armeabi-v7a`, and `x86_64`) and retains a universal APK. CI records the exact size of each split and warns when an individual package is at least 100,000,000 bytes. Do not record estimated size savings as a result until the beta.13 workflow produces its artifacts.

Direct-download distribution offers two trade-offs:

- A universal APK is easiest to explain and install, but contains the native runtime for every included ABI and therefore has the largest file size.
- ABI-specific APKs contain only one architecture's native payload and should be smaller; users must choose a package compatible with their device. Android App Bundle distribution delegates ABI selection to Google Play, but a CI-produced unsigned AAB is only a build candidate, not a publishable production artifact.

The beta.13 workflow also builds an optimized release AAB candidate to catch release-variant regressions early. Production distribution must use a permanent owner-controlled upload/signing key supplied through a protected CI secret or equivalent secure signing setup; never commit that key to the repository.

The x86_64 Rust/C++ target improves build/test coverage on compatible Android emulators, but does not resolve the third-party FFmpeg 0.18.1 16 KB memory-page alignment issue. Keep the conservative conversion gate until the bundled media payload is corrected and validated on affected devices.

An ABI packaging change is not a media-engine redesign. Local file conversion remains a separate future phase with file-picker/URI access, explicit input/output format planning, cancellation, partial-output cleanup, and format-by-format license and device compatibility validation.

## Local-file conversion path (beta.14)

Beta.14 adds a separate local-file engine rather than feeding filesystem URIs into yt-dlp. Android's document picker provides the selected content URI; UBAHVO copies the input into app-private cache so the native FFmpeg process can read a regular file path. FFprobe inspects streams and duration before conversion. The planner chooses only encoders reported by the bundled FFmpeg executable and rejects unsupported input/output combinations before a conversion process begins.

- MP3 requires an audio stream and an available MP3 encoder (`libmp3lame` or `libshine`).
- MP4 requires a video stream and an available video encoder (`libx264` or `mpeg4`); when audio is present, an AAC encoder is also required.
- Conversion uses argument arrays through `ProcessBuilder`, not a shell string. Inputs/outputs are kept under a job-specific private cache directory, progress uses FFmpeg's machine-readable progress output, cancellation terminates the active subprocess, and temporary files are deleted in `finally`.
- Completed outputs are published through the existing MediaStore path and appear in recent history. The document picker is used instead of broad storage permissions.
- Automated JVM tests cover probe parsing, attached album art, encoder detection/selection, invalid stream combinations, and progress math. They cannot establish that every upstream binary/codec runs on real devices; physical-device tests remain required.
- The feature continues to use the current engine readiness/page-size gate. It does not bypass the documented 16 KB page-size limitation in the third-party FFmpeg payload.

## Measured beta.13 artifact sizes

The successful CI run for commit `a26d10f05eefef2b715ac8291772fdbb6ca2d41e` measured these final downloadable artifacts:

| Artifact | Bytes | Approx. decimal MB |
| --- | ---: | ---: |
| Universal APK (ARM + x86_64) | 160,842,926 | 160.84 MB |
| `arm64-v8a` APK | 62,937,041 | 62.94 MB |
| `armeabi-v7a` APK | 56,174,703 | 56.17 MB |
| `x86_64` APK | 65,959,270 | 65.96 MB |
| Optimized unsigned AAB candidate | 157,591,726 | 157.59 MB |

The universal APK is 60,842,926 bytes above the 100,000,000-byte target, so the target is **not met** by the one-file download. The three ABI-specific APKs remain below that threshold individually and avoid bundling the unused architecture's native payload. The AAB is a build artifact, not the same thing as a device-specific download: Google Play normally generates optimized install APKs from a properly signed App Bundle. The measured AAB remains unsigned and must not be represented as ready to publish.

These sizes come from the CI run for beta.13 and are not guarantees for future builds; record the new workflow summary each time a release changes native dependencies, scripts, resources, or build settings.

## App Bundle build separation

The beta.13 CI attempt confirmed that Android Gradle Plugin fails when ABI-split APK resource shrinking and AAB packaging are requested in the same task graph. CI now builds the split/universal APKs first, then performs a second Gradle invocation with `-Pubahvo.bundleOnly=true` so ABI splits are disabled for the AAB build. The AAB remains an unsigned candidate until secure production signing is configured.
