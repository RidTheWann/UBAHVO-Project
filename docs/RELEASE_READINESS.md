# Release readiness and handoff

## Release channel

The first automated release is intentionally a beta prerelease. CI produces architecture-specific installable APKs from the Android debug variant, attaches SHA-256 checksums, and records known limitations. This repository does not currently have a persistent private Android release-signing key configured; therefore this workflow must not be represented as a production-signed stable release.

## Automated gates

The Android CI workflow runs:
- `:app:testDebugUnitTest`
- `:app:assembleDebug` for all configured ABIs
- A packaging check that verifies every ABI APK includes its corresponding `libc++_shared.so`
- Unit tests that exercise safe replacement and validation of the staged native runtime
- Artifact upload of the ARM64 APK for quick testing.

The main-branch release workflow repeats test/build gates before publishing a prerelease and attaches APKs for arm64-v8a, armeabi-v7a, x86_64 and x86. It skips publication if that version's GitHub release already exists.

## Known release blockers / boundaries

1. **16 KB memory-page compatibility:** upstream issue https://github.com/yausername/youtubedl-android/issues/365 reports that some shared libraries inside the bundled FFmpeg payload remain 4 KB-aligned in youtubedl-android 0.18.1. Since a native linker failure may terminate the process, this application disables conversion unless the OS reports a 4 KB page size. This guard is a safety mitigation, not a fix to the upstream binary.
2. **Missing FFmpeg C++ runtime in upstream artifact:** issue https://github.com/yausername/youtubedl-android/issues/369 reports that FFmpeg 0.18.1's internal Rubber Band library needs `libc++_shared.so`, missing from the published FFmpeg payload. This app packages the ABI-matched runtime from pinned NDK 27.2.12479018 and stages it into FFmpeg's private `usr/lib` directory, which the wrapper places on `LD_LIBRARY_PATH`. The build verifies that the runtime is packaged, and unit tests verify file-staging behavior; the native conversion itself still needs a physical-device smoke test.
3. **Physical-device smoke tests:** CI does not perform real YouTube network downloads, FFmpeg remux/extraction, playback, cancellation, or MediaStore checks on physical devices. These remain unverified by CI.
4. **Debug signing:** beta APKs use the runner-generated debug signing key. Do not promise seamless in-place updates between separately generated beta APKs. A production stable release requires a protected release keystore and stable-key update testing.
5. **Third-party version status:** youtubedl-android 0.18.1 is documented upstream as a pre-release. Re-evaluate version provenance, bundled yt-dlp freshness, native ABI alignment and licenses before stable distribution.
6. **Service and rights compliance:** the ownership/permission checkbox is an attestation, not a technical authorization mechanism. Use only for media the user owns or has permission to download. Do not add DRM bypass, cookie import, or private/paid-content access.

## Criteria before calling it stable

- Replace or rebuild FFmpeg payloads so every native library used at runtime is confirmed 16 KB compatible and the upstream libc++ omission is fixed.
- Run smoke tests on representative ARM64 physical devices, including 4 KB and 16 KB page-size environments.
- Verify MP4/MP3 output correctness, no partial-file leaks, cancellation semantics, storage publication and playback.
- Audit licenses and provide notices/sources for exact redistributed native binaries, including the NDK C++ runtime.
- Configure protected release signing material and test update installation with the same signing certificate.
- Produce and verify signed release APK/AAB, SHA-256, SBOM/dependency inventory, and release notes.
