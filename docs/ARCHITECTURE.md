# Architecture

The application uses a small layered structure with unidirectional UI state flow.

- `ui/ConverterScreen.kt`: Jetpack Compose presentation and user interactions.
- `ui/ConverterViewModel.kt`: input validation, operation state, progress and cancellation.
- `data/DownloadRepository.kt`: yt-dlp / FFmpeg request construction and execution off the main thread.
- `data/OutputPublisher.kt`: copies the final artifact into public Downloads using MediaStore's pending-item lifecycle.
- `data/YouTubeUrlValidator.kt`: validates HTTPS YouTube video URLs and rejects look-alike domains.
- `YoutubeConvertApplication.kt`: initializes native media dependencies once per process.

## Media processing

Downloads are staged in the app-specific external files directory. Once the expected output file exists, the repository publishes it to `Download/YoutubeConvert` through MediaStore and then removes the staging directory. The app requests no broad storage permission.

MP4 selection prefers MP4 video and M4A audio streams and asks FFmpeg to remux to MP4. MP3 mode extracts audio with FFmpeg's MP3 encoder. Certain videos or codecs may not be available in the chosen output mode; failures are surfaced in the UI instead of silently saving the wrong extension.

## Reliability boundaries

- A single operation runs at a time per screen instance.
- The UI provides an explicit cancellation action.
- Native engine initialization and conversion errors are caught and reported.
- The validator accepts one supported video URL; `--no-playlist` prevents playlist expansion.
- The app has no cookie import, login automation, DRM bypass, or private-content access feature.
