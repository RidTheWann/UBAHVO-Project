package com.ridthewann.ubahvo.data

import android.content.Context
import android.os.Environment
import android.net.Uri
import com.ridthewann.ubahvo.core.OutputMode
import com.ridthewann.ubahvo.core.VideoQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class YtDlpUpdateResult(
    val updated: Boolean,
    val version: String?,
    val versionName: String?
)

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val publisher = OutputPublisher(appContext)
    private val localMediaConverter = LocalMediaConverter(appContext, publisher)

    suspend fun updateYtDlp(): YtDlpUpdateResult = withContext(Dispatchers.IO) {
        StableYtDlpUpdater(appContext).update()
    }

    suspend fun recentOutputs(limit: Int = 5): List<RecentOutput> = withContext(Dispatchers.IO) {
        publisher.recentOutputs(limit)
    }

    suspend fun convertLocalFile(
        uri: Uri,
        displayName: String,
        mode: OutputMode,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, outputLine: String) -> Unit
    ): PublishedOutput = localMediaConverter.convert(uri, displayName, mode, processId, onProgress)

    suspend fun download(
        url: String,
        mode: OutputMode,
        quality: VideoQuality,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, outputLine: String) -> Unit
    ): PublishedOutput = withContext(Dispatchers.IO) {
        val baseDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.cacheDir
        val outputDirectory = File(baseDirectory, "UBAHVO/$processId")
        if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory) {
            throw IOException("Could not create a temporary output directory.")
        }

        try {
            // UbahvoApplication initializes both engines once per Android process and gates
            // this path with engineReady. Re-initializing both native runtimes here repeats work
            // for each Save tap; process recreation runs Application.onCreate again automatically.
            val request = buildRequest(url, mode, quality, outputDirectory)
            val callback: (Float, Long, String) -> Unit = { progress, eta, line ->
                onProgress(progress.coerceIn(0f, 100f), eta.coerceAtLeast(0L), line)
            }

            YoutubeDL.getInstance().execute(request, processId, true, callback)

            val outputFile = outputDirectory.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(".part", ignoreCase = true) }
                .filter { it.extension.equals(mode.extension, ignoreCase = true) }
                .maxByOrNull(File::lastModified)
                ?: throw IOException(
                    "No .${mode.extension} output was created. Try another quality or check whether the source is available."
                )

            publisher.publish(outputFile, mode.mimeType)
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    fun cancel(processId: String) {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }
        localMediaConverter.cancel(processId)
    }

    private fun buildRequest(
        url: String,
        mode: OutputMode,
        quality: VideoQuality,
        outputDirectory: File
    ): YoutubeDLRequest {
        val template = File(outputDirectory, "%(title).180B [%(id)s].%(ext)s").absolutePath
        return YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--no-colors")
            addOption("--newline")
            // Print the title during this same extraction, avoiding a separate --dump-json run.
            addOption("--print", "before_dl:UBAHVO_TITLE:%(title)s")
            // Explicit progress keeps the transfer indicator visible even though --print implies quiet.
            addOption("--progress")
            // The upstream wrapper disables yt-dlp's cache unless --cache-dir is explicit.
            // Retain its small extractor/player-function cache between conversion attempts.
            addOption("--cache-dir", File(appContext.cacheDir, "yt-dlp-cache").absolutePath)
            addOption("--no-mtime")
            addOption("--no-part")
            addOption("-o", template)

            when (mode) {
                OutputMode.MP3 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                OutputMode.MP4 -> {
                    addOption("-f", VideoFormatSelector.forQuality(quality))
                    // DASH/HLS sources can expose video as many small fragments. Four concurrent
                    // fragment requests improve throughput on high-bitrate 4K/8K sources without
                    // requiring a separate downloader binary or changing the source's codec.
                    addOption("--concurrent-fragments", "4")
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                }
            }
        }
    }
}
