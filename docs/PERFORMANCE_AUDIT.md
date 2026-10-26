# UBAHVO performance and download-size audit

Audit date: 2026-10-27
Scope: link-paste-to-save latency, native-language suitability, automatic link-support maintenance, and APK download footprint.

## Confirmed findings before optimization

1. The save path called `DownloadRepository.getInfo()`, which runs `yt-dlp --dump-json`, then called `DownloadRepository.download()`, which starts yt-dlp again for the same URL. This duplicated the first network extraction and Python process startup before the actual download.
2. The upstream `youtubedl-android` wrapper adds `--no-cache-dir` unless an explicit `--cache-dir` is supplied. That prevents reuse of yt-dlp's persistent extractor cache between requests.
3. The conversion pipeline is dominated by network extraction, subprocess startup, media download, and FFmpeg post-processing. The application Kotlin code is not performing a CPU-heavy media transform that would benefit from moving it to Rust or another language.
4. The universal beta.8 APK was measured through GitHub Release metadata at 107,183,163 bytes. It includes both ARM ABIs and bundled Python/FFmpeg runtime payloads. The CI log also shows `libpython.zip.so` and `libffmpeg.zip.so` are archive payloads that `llvm-strip` does not recognize as ELF shared objects; blindly stripping them is not a valid size optimization.
5. Previous Android CI ran unit tests and assembled the APK successfully, then failed in the post-build DEX verification script due to an over-escaped grep pattern. That verification pattern has been corrected to match the disassembly form.

## Selected changes

- Remove the metadata-only `getInfo()` invocation from the save path. The same yt-dlp run now emits its title using `--print before_dl:UBAHVO_TITLE:%(title)s`; the UI can show a title without a second extraction.
- Explicitly enable yt-dlp progress output and a cache directory under Android's app cache directory. The cache is expendable and does not pollute Downloads.
- Throttle UI state updates to meaningful progress, ETA, title, and diagnostic changes rather than recomposing the screen for every unchanged subprocess output line.
- Check for stable yt-dlp support updates automatically in the background on a daily success interval. A failed attempt sets a six-hour retry window for the next app launch. Downloads remain enabled during automatic updates, and the existing verified atomic installer protects the active script from partial writes. A clearly labelled manual action remains available.
- Restrict Android dependency resources to Indonesian and English, and remove Android UI-testing/preview dependencies that are not used by this repository's current test suite.
- Add tests for title marker parsing and the automatic-update schedule.
- Report the actual universal APK size in CI. The 100,000,000-byte target is a measured budget, not an assumed result.

## Why no new Rust/C/C++/C# module is added

Rust/JNI or another native language does not remove YouTube's network latency, the Python/yt-dlp extraction step, or the duplicated process that was found in the old save path. FFmpeg already supplies native media processing, implemented in C/C++. Adding another runtime and JNI boundary would increase code, build complexity, and potentially APK size without targeting the measured bottleneck. C# would add an additional managed-runtime stack without a justified role in this Android/Kotlin app.

Native code should be introduced only if profiling identifies a CPU-bound operation in UBAHVO-owned code. The current best choice is to keep UI/orchestration in Kotlin, retain FFmpeg for media work, and remove redundant work from the network-bound save path.

## Verification and remaining limits

- Unit tests cover the new parser and scheduling policy.
- CI checks the minified ZIP extra-field constructor used by Apache Commons Compress reflection, confirms both ARM ABIs and C++ runtimes exist, and reports the final APK byte count.
- CI cannot prove end-to-end improvement in real YouTube extraction latency. Physical-device smoke testing should compare time from tapping **Simpan** to the first visible download progress, then test one authorized MP3 and one authorized MP4.
- The universal APK baseline is above 100 MB. Kotlin/R8/resource trimming may reduce it, but the dominant bundled Python and FFmpeg payloads are architecture-specific native archives. A firm sub-100 MB universal APK may require ABI-specific distribution or a larger on-demand-runtime redesign; neither should be implemented blindly because both affect installation simplicity and first-use behavior.
