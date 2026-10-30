# Release readiness and handoff

## Release channel

The automated release is intentionally a beta prerelease. CI produces a universal APK plus ABI-specific APKs for arm64-v8a, armeabi-v7a, and x86_64, attaches SHA-256 checksums and release notes, and builds an unsigned optimized AAB candidate. R8 code/resource shrinking and compressed native-library packaging are enabled. APKs are debug-signed with the CI runner's ephemeral key; this workflow must not be represented as a production-signed stable release.

## Current beta milestone

`0.1.0-beta.18` is the current beta milestone and retains the UBAHVO application ID `com.ridthewann.ubahvo`. It publishes a universal APK plus ABI-specific builds for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. CI also builds an unsigned optimized AAB candidate. The release workflow enables R8/resource shrinking and attaches SHA-256 checksums. Beta APKs are generated with an ephemeral CI debug signing key; installation over a different debug-signed build may require uninstalling the older APK. Back up important outputs before replacing an existing beta.

Beta.18 fixes the first-use MP4/MP3 Save-button deadlock caused by checking lazy initialization completion instead of the native compatibility gate. It also synchronizes the yt-dlp updater with remote extraction and includes the TikTok-scoped request workaround. These changes do not make every extractor universally reliable; public access and upstream site changes still determine provider support.

## Automated gates

The Android CI workflow runs:

- `:app:testDebugUnitTest` and locked Rust unit tests
- `:app:assembleDebug` to create the universal APK and ABI-specific APKs
- `:app:bundleRelease` to create an unsigned optimized AAB candidate
- DEX/package verification and recursive ELF auditing, including native libraries embedded in nested ZIP payloads
- Artifact upload of APK variants and the unsigned AAB candidate

The main-branch release workflow repeats the tests/build gates before publishing the beta prerelease with universal and ABI-specific APKs, SHA-256 checksums, and release notes. The AAB remains unsigned until a permanent owner-controlled upload key is configured. The workflow skips publication if that version’s GitHub release already exists.

## Known release blockers / boundaries

1. **16 KB memory-page compatibility:** upstream issue https://github.com/yausername/youtubedl-android/issues/365 reports that some shared libraries inside the bundled FFmpeg payload remain 4 KB-aligned in youtubedl-android 0.18.1. Since a native linker failure may terminate the process, this app disables conversion unless the OS reports a 4 KB page size. This guard is a safety mitigation, not a fix to the upstream binary; a universal APK does not remove this blocker.
2. **Missing FFmpeg C++ runtime in upstream artifact:** issue https://github.com/yausername/youtubedl-android/issues/369 reports that FFmpeg 0.18.1’s internal Rubber Band library needs `libc++_shared.so`, missing from the published FFmpeg payload. The app stages an ABI-matched runtime from pinned NDK 27.2.12479018 into FFmpeg’s private `usr/lib` directory. CI verifies that the runtime is packaged and unit tests verify staging behavior; native conversion still needs physical-device smoke tests.
3. **Physical-device smoke tests:** CI does not perform real YouTube network downloads, FFmpeg remux/extraction, playback, cancellation, or MediaStore checks on physical devices. These remain unverified by CI.
4. **Debug signing:** beta APKs use the runner-generated debug signing key. Do not promise seamless in-place updates between separately generated beta APKs. A production stable release requires a protected release keystore and stable-key update testing.
5. **Third-party version status:** youtubedl-android 0.18.1 is documented upstream as a pre-release. Re-evaluate version provenance, bundled yt-dlp freshness, native ABI alignment, 16 KB compatibility, and licenses before stable distribution.
6. **Package migration:** beta.8 uses `com.ridthewann.ubahvo`, so it cannot update the previous `com.ridthewann.youtubeconvert` package. Test the new app first and back up important exports before uninstalling an older build.
7. **Service and rights compliance:** the ownership/permission checkbox is an attestation, not a technical authorization mechanism. Use only media the user owns or has permission to download. Do not add DRM bypass, cookie import, or private/paid-content access.

## Criteria before calling it stable

- Replace or rebuild FFmpeg payloads so every native library used at runtime is confirmed 16 KB compatible and the upstream libc++ omission is fixed.
- Run smoke tests on representative ARM64 physical devices, including 4 KB and 16 KB page-size environments.
- Verify MP4/MP3 output correctness, no partial-file leaks, cancellation semantics, storage publication and playback.
- Audit licenses and provide notices/sources for exact redistributed native binaries, including the NDK C++ runtime.
- Configure protected release signing material and test update installation with the same signing certificate.
- Produce and verify signed release APK/AAB, SHA-256, SBOM/dependency inventory, and release notes.