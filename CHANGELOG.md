# Changelog

All notable user-facing changes to UBAHVO are documented here.

## 0.1.0-beta.7 — 2026-10-26

- Renamed local debug APK files, CI artifacts, GitHub prerelease assets, release notes, and checksum patterns to UBAHVO.
- Renamed the Android application class, Compose theme function, and XML theme resource to match the product name while keeping the installed applicationId unchanged.
- Saved new exports and temporary work files under UBAHVO-branded directories.
- Kept previously exported files in Download/YoutubeConvert in place and included that legacy location in recent-output history.
- Kept the current YouTube URL → MP4/MP3 feature scope; universal conversion is still a later stage.

## 0.1.0-beta.6 — 2026-10-26

- Reworked main workflow wording into clear, numbered steps and simplified the format, link, permission, progress, and output labels.
- Made the main action reflect the selected result: save video or save audio.
- Replaced user-facing downloader/initialization errors with actionable plain-language guidance.
- Allowed choice cards and the main action to grow more naturally with larger system text.
- Kept conversion, updater verification, native compatibility guards, storage behavior, and app identity unchanged.

## 0.1.0-beta.5 — 2026-10-26

- Introduced the UBAHVO product name, in-app header branding, developer credit, and a non-YouTube-specific launcher mark.
- Preserved the Android applicationId, Kotlin package names, and existing Downloads/YoutubeConvert output folder to protect upgrade and file compatibility.
- Kept the current feature scope explicit: supported YouTube links to MP4/MP3 only; universal conversion is planned for a later stage.


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
