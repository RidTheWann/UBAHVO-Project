package com.ridthewann.youtubeconvert.data

import android.content.Context
import android.os.Environment
import com.ridthewann.youtubeconvert.core.OutputMode
import com.ridthewann.youtubeconvert.core.VideoQuality
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
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

    suspend fun getInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        YoutubeDL.getInstance().getInfo(request)
    }

    suspend fun updateYtDlp(): YtDlpUpdateResult = withContext(Dispatchers.IO) {
        val status = YoutubeDL.getInstance().updateYoutubeDL(
            appContext,
            YoutubeDL.UpdateChannel.STABLE
        ) ?: throw IOException("Could not determine yt-dlp update status.")

        YtDlpUpdateResult(
            updated = status == YoutubeDL.UpdateStatus.DONE,
            version = YoutubeDL.getInstance().version(appContext),
            versionName = YoutubeDL.getInstance().versionName(appContext)
        )
    }

    suspend fun download(
        url: String,
        mode: OutputMode,
        quality: VideoQuality,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, outputLine: String) -> Unit
    ): PublishedOutput = withContext(Dispatchers.IO) {
        val baseDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.cacheDir
        val outputDirectory = File(baseDirectory, "YoutubeConvert/$processId")
        if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory) {
            throw IOException("Could not create a temporary output directory.")
        }

        try {
            // Re-init is idempotent and protects callers if Android recreates the process.
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)

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
            addOption("--newline")
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
                    val height = quality.maxHeight
                    val format = if (height == null) {
                        "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                    } else {
                        "bestvideo[ext=mp4][height<=$height]+bestaudio[ext=m4a]/" +
                            "best[ext=mp4][height<=$height]/best[height<=$height]"
                    }
                    addOption("-f", format)
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                }
            }
        }
    }
}
