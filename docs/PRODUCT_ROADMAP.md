# UBAHVO product roadmap

This document defines the implementation order for the app. Each stage should be reviewed and tested before the next begins. The roadmap must not be interpreted as a claim that planned capabilities already exist.

## Product promise

UBAHVO should be understandable without technical knowledge. The target audience includes children, adults, and older users. The app should explain actions in plain Indonesian first, clearly show what will happen to a file, and never make an irreversible action or an unclear choice the default.

## Stage 1 — Branding

Status: beta.13 adds ABI-specific direct-download APKs, an x86_64 build, and an unsigned optimized AAB candidate in CI. The app identity remains `com.ridthewann.ubahvo`; this packaging update does not change the application ID.

- Use UBAHVO as the user-facing product name and launcher identity.
- Keep developer attribution secondary: “by RidTheWann”.
- Use `com.ridthewann.ubahvo` as the Android application ID and Kotlin namespace/package. New outputs use `Download/UBAHVO`; history queries `Download/YoutubeConvert` and the old package-owner ID where Android permits access, without moving legacy files.
- Keep product claims accurate: the current beta processes supported HTTPS media URLs from multiple yt-dlp extractors into MP4 video or MP3 audio. Site coverage is not guaranteed and playlists/batch jobs are not supported.
- The beta.11 Rust/C++ module is a narrow URL preflight. It is not presented as a replacement for yt-dlp or FFmpeg, and it must not be advertised as a measured media-conversion speedup.

## Distribution and release engineering

- Keep the universal APK as the simplest manual-install option, but publish ABI-specific APKs to reduce download size for users who can identify their architecture.
- Include `arm64-v8a` and `armeabi-v7a` for ARM phones and `x86_64` for compatible emulator/Intel Android environments. Confirm the upstream media runtime works for each target; packaging success alone is not device compatibility proof.
- Build an optimized Android App Bundle candidate in CI. Do not describe an unsigned AAB as production-ready or publish it to Google Play until a permanent owner-controlled upload key and protected signing workflow are configured.
- Publish SHA-256 checksums for each downloadable APK and report actual artifact sizes. Keep the 100,000,000-byte universal target as a measured goal, not a pass/fail claim made before building.
- Continue to label CI debug-signed APKs as beta builds. A stable release requires production signing, reproducible release verification, and physical-device smoke testing.

## Stage 2 — Beginner-friendly and accessible UI

The first beginner-friendly UI pass is being introduced in beta.6. Real-device accessibility and workflow smoke tests remain necessary before a stable release.

- Make the main workflow read from top to bottom: choose what to make, paste or share a link, choose a quality only when relevant, review permission guidance, then start.
- Prefer clear action labels such as “Pilih video”, “Jadikan MP4”, “Jadikan MP3”, “Mulai”, “Batalkan”, and “Buka hasil” where those labels accurately describe the action.
- Use plain-language helper text and actionable errors. Explain what the user can do next; do not show raw stack traces or internal dependency messages as the primary error.
- Provide visible empty, loading, progress, completed, cancelled, and failed states.
- Keep important touch targets comfortable, text readable at system font scaling, colors sufficiently contrasted, and icons paired with text labels where an icon alone could be ambiguous.
- Preserve screen-reader semantics, meaningful content descriptions, keyboard/focus behavior, and Android back navigation.
- Do not redesign the downloader/updater internals in the same change. Add or update Compose UI tests and run unit tests before merging.

Acceptance gate:
- A first-time user can identify the main action without reading technical setup instructions.
- The user can tell which format is selected, whether work is running, where the result was saved, and what to do after a failure.
- Existing MP3/MP4, update, cancellation, history, and compatibility safeguards remain intact.

## Stage 3 — Universal conversion foundation

Current step: broaden link-based media extraction first. Local-file conversion is a separate engine and permission path; its advertised formats must be implemented and tested before the UI claims broad format support.

Begin only after the simplified UI and its tests are stable.

- Separate input discovery, format detection, conversion planning, execution, and output publishing from the current media-link download flow.
- Model supported input and output formats explicitly. Reject unsupported or unsafe conversions before starting a job.
- Evaluate libraries and native components by supported formats, license obligations, ABI and Android version support, memory use, performance, and security maintenance.
- Add local-file selection and media types incrementally rather than advertising every format at once.
- Make progress, cancellation, partial-output cleanup, output naming, storage access, and error recovery consistent across engines.
- Keep source-service downloading and local-file conversion distinct: they have different permissions, legal constraints, and failure modes.

Acceptance gate:
- Every advertised conversion has a verified implementation, dependency/license review, automated tests, and physical-device smoke tests for supported Android configurations.
- Existing installations and files remain compatible unless a separate migration is designed, tested, and documented.

## Release discipline

- Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` on a configured Android development environment.
- Verify the produced APK's label, icon, version, package ID, and required native libraries.
- Smoke-test MP3, MP4, cancellation, updater success/failure, history, and file open/share actions on a real device.
- Keep debug-signed beta artifacts clearly labelled as test builds. Do not describe a beta as a stable production release.
