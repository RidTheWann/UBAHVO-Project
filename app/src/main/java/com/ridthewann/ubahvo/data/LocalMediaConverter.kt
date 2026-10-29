package com.ridthewann.ubahvo.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ridthewann.ubahvo.core.OutputMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal class LocalMediaConversionException(
    val userMessage: String,
    cause: Throwable? = null
) : IOException(userMessage, cause)

/**
 * Converts a document-picker URI with the FFmpeg/FFprobe executables bundled by youtubedl-android.
 * SAF input is copied into private cache before a subprocess reads it; no shell interpolation or
 * broad storage permission is used. Temporary files are removed after success, failure, or cancel.
 */
internal class LocalMediaConverter(
    private val context: Context,
    private val publisher: OutputPublisher
) {
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    suspend fun convert(
        uri: Uri,
        displayName: String,
        mode: OutputMode,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, outputLine: String) -> Unit
    ): PublishedOutput = withContext(Dispatchers.IO) {
        val workDirectory = File(File(context.cacheDir, WORK_DIRECTORY), processId)
        if (!workDirectory.mkdirs() && !workDirectory.isDirectory) {
            throw LocalMediaConversionException("Ruang kerja sementara tidak dapat disiapkan.")
        }

        try {
            val inputSize = querySize(uri)
            val available = workDirectory.usableSpace
            if (inputSize != null && inputSize > (available - MINIMUM_FREE_SPACE_RESERVE).coerceAtLeast(0L)) {
                throw LocalMediaConversionException(
                    "Ruang penyimpanan sementara tidak cukup. Kosongkan penyimpanan lalu coba lagi."
                )
            }

            onProgress(0f, 0L, "LOCAL_MODE:${if (inputSize == null) "indeterminate" else "determinate"}")
            onProgress(0f, 0L, "LOCAL_STAGE:Menyalin file ke area kerja…")
            val inputFile = File(workDirectory, safeInputName(displayName))
            copyInput(uri, inputFile, inputSize, onProgress)

            if (workDirectory.usableSpace < MINIMUM_FREE_SPACE_RESERVE) {
                throw LocalMediaConversionException(
                    "Ruang kosong tidak cukup untuk membuat file hasil. Kosongkan penyimpanan lalu coba lagi."
                )
            }

            val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            val ffmpeg = File(nativeLibraryDir, FFMPEG_EXECUTABLE)
            val ffprobe = File(nativeLibraryDir, FFPROBE_EXECUTABLE)
            if (!ffmpeg.isFile || !ffmpeg.canExecute() || !ffprobe.isFile || !ffprobe.canExecute()) {
                throw LocalMediaConversionException(
                    "Komponen media tidak tersedia untuk arsitektur perangkat ini. Coba perbarui UBAHVO."
                )
            }

            onProgress(10f, 0L, "LOCAL_MODE:indeterminate")
            onProgress(10f, 0L, "LOCAL_STAGE:Menganalisis media…")
            val probeOutput = runProcess(
                processId,
                listOf(
                    ffprobe.absolutePath, "-v", "error",
                    "-show_entries", "format=duration:stream=codec_type,codec_name,width,height:stream_disposition=attached_pic",
                    "-of", "json", inputFile.absolutePath
                ),
                "File tidak dapat dibaca sebagai media audio atau video."
            )
            val mediaInfo = try {
                LocalMediaConversionPlanner.parseProbeJson(probeOutput)
            } catch (error: Exception) {
                throw LocalMediaConversionException(
                    "File ini bukan media audio/video yang didukung, atau filenya rusak.",
                    error
                )
            }

            onProgress(12f, 0L, "LOCAL_MODE:${if (mediaInfo.durationSeconds == null) "indeterminate" else "determinate"}")
            onProgress(12f, 0L, "LOCAL_STAGE:Mempersiapkan konversi…")
            val encodersOutput = runProcess(
                processId,
                listOf(ffmpeg.absolutePath, "-hide_banner", "-encoders"),
                "Daftar encoder media tidak dapat dibaca."
            )
            val outputFile = File(workDirectory, safeOutputName(displayName, mode.extension))
            val arguments = try {
                LocalMediaConversionPlanner.buildArguments(
                    mode, mediaInfo, LocalMediaConversionPlanner.encoderNames(encodersOutput),
                    inputFile.absolutePath, outputFile.absolutePath
                )
            } catch (error: IllegalArgumentException) {
                val message = when (error.message) {
                    "MP4 requires a video stream." ->
                        "File ini tidak memiliki trek video. Pilih Audio MP3 atau gunakan file video."
                    "MP3 requires an audio stream." ->
                        "File ini tidak memiliki trek audio yang dapat dijadikan MP3."
                    "The bundled FFmpeg build has no MP3 encoder." ->
                        "Encoder MP3 tidak tersedia pada komponen media perangkat ini."
                    "The bundled FFmpeg build has no supported MP4 video encoder." ->
                        "Encoder video MP4 tidak tersedia pada komponen media perangkat ini."
                    "The bundled FFmpeg build has no AAC audio encoder for MP4." ->
                        "Encoder audio AAC tidak tersedia untuk membuat MP4 pada perangkat ini."
                    else -> "File ini tidak dapat dikonversi ke format yang dipilih."
                }
                throw LocalMediaConversionException(message, error)
            }

            onProgress(15f, 0L, "LOCAL_MODE:${if (mediaInfo.durationSeconds == null) "indeterminate" else "determinate"}")
            onProgress(
                15f, 0L,
                if (mode == OutputMode.MP3) "LOCAL_STAGE:Mengonversi audio menjadi MP3…"
                else "LOCAL_STAGE:Mengonversi video menjadi MP4…"
            )
            runProcess(
                processId,
                listOf(ffmpeg.absolutePath) + arguments,
                "Konversi gagal. Codec sumber mungkin tidak didukung atau penyimpanan tidak mencukupi.",
                captureOutput = false,
                onLine = { line ->
                    if (line.startsWith("out_time=")) {
                        LocalMediaConversionPlanner.progressPercent(
                            line.substringAfter('='),
                            mediaInfo.durationSeconds
                        )?.let { onProgress(it, 0L, "LOCAL_PROGRESS") }
                    } else if (line == "progress=end") {
                        onProgress(95f, 0L, "LOCAL_PROGRESS")
                    }
                }
            )

            if (!outputFile.isFile || outputFile.length() <= 0L) {
                throw LocalMediaConversionException("File hasil kosong. Coba media lain atau periksa ruang penyimpanan.")
            }
            onProgress(98f, 0L, "LOCAL_STAGE:Menyimpan hasil ke Download/UBAHVO…")
            publisher.publish(outputFile, mode.mimeType)
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    fun cancel(processId: String) {
        val process = activeProcesses[processId] ?: return
        runCatching { process.destroy() }
        if (process.isAlive) runCatching { process.destroyForcibly() }
    }

    private suspend fun copyInput(
        uri: Uri,
        destination: File,
        expectedSize: Long?,
        onProgress: (Float, Long, String) -> Unit
    ) {
        val coroutineContext = currentCoroutineContext()
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (error: Exception) {
            throw LocalMediaConversionException(
                "File tidak dapat dibaca. Pilih ulang file dari perangkat atau penyedia file.",
                error
            )
        } ?: throw LocalMediaConversionException("File tidak dapat dibuka. Pilih ulang file dari perangkat.")

        try {
            input.buffered().use { source ->
                destination.outputStream().buffered().use { target ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copied = 0L
                    var lastProgress = -1f
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        val required = copied + count
                        val free = destination.parentFile?.usableSpace ?: 0L
                        if (required > free - MINIMUM_FREE_SPACE_RESERVE) {
                            throw LocalMediaConversionException(
                                "Penyimpanan sementara penuh saat menyalin file. Kosongkan ruang lalu coba lagi."
                            )
                        }
                        target.write(buffer, 0, count)
                        copied = required
                        val percent = if (expectedSize != null && expectedSize > 0L) {
                            (2.0 + 8.0 * (copied.toDouble() / expectedSize).coerceIn(0.0, 1.0)).toFloat()
                        } else 3f
                        if (percent - lastProgress >= 1f) {
                            lastProgress = percent
                            onProgress(percent, 0L, "LOCAL_PROGRESS")
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is LocalMediaConversionException) throw error
            throw LocalMediaConversionException(
                "File tidak dapat disalin ke area kerja. Periksa akses file dan ruang penyimpanan.",
                error
            )
        }

        if (!destination.isFile || destination.length() <= 0L) {
            throw LocalMediaConversionException("File yang dipilih kosong atau tidak dapat dibaca.")
        }
    }

    private suspend fun runProcess(
        processId: String,
        command: List<String>,
        failureMessage: String,
        captureOutput: Boolean = true,
        onLine: (String) -> Unit = {}
    ): String {
        val coroutineContext = currentCoroutineContext()
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        val ffmpegLibraryDir = File(
            context.noBackupFilesDir,
            "youtubedl-android/packages/ffmpeg/usr/lib"
        ).absolutePath
        builder.environment()["LD_LIBRARY_PATH"] = listOf(
            ffmpegLibraryDir, context.applicationInfo.nativeLibraryDir,
            System.getenv("LD_LIBRARY_PATH").orEmpty()
        ).filter { it.isNotBlank() }.distinct().joinToString(":")
        builder.environment()["HOME"] = context.noBackupFilesDir.absolutePath
        builder.environment()["TMPDIR"] = context.cacheDir.absolutePath

        val process = try {
            builder.start()
        } catch (error: Exception) {
            throw LocalMediaConversionException("Komponen media tidak dapat dijalankan pada perangkat ini.", error)
        }
        activeProcesses[processId] = process
        val captured = StringBuilder()
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    onLine(line)
                    if (captureOutput && captured.length < MAX_CAPTURED_OUTPUT_CHARS) {
                        captured.append(line).append('\n')
                    }
                }
            }
            val exitCode = process.waitFor()
            coroutineContext.ensureActive()
            if (exitCode != 0) throw LocalMediaConversionException(failureMessage)
            return captured.toString()
        } catch (cancelled: CancellationException) {
            runCatching { process.destroyForcibly() }
            throw cancelled
        } catch (error: LocalMediaConversionException) {
            runCatching { process.destroyForcibly() }
            throw error
        } catch (error: Exception) {
            runCatching { process.destroyForcibly() }
            throw LocalMediaConversionException(failureMessage, error)
        } finally {
            activeProcesses.remove(processId, process)
        }
    }

    private fun querySize(uri: Uri): Long? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index).takeIf { it > 0L }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun safeInputName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
            .filter { it.isLetterOrDigit() }.take(8)
        return if (extension.isBlank()) "source_media" else "source_media.$extension"
    }

    private fun safeOutputName(name: String, extension: String): String {
        val base = name.substringBeforeLast('.', name)
            .map { if (it.isLetterOrDigit() || it in " ._()-[]") it else '_' }
            .joinToString("").trim().trim('.').take(100).ifBlank { "converted_media" }
        return "$base.$extension"
    }

    companion object {
        private const val WORK_DIRECTORY = "ubahvo-local-conversion"
        private const val FFMPEG_EXECUTABLE = "libffmpeg.so"
        private const val FFPROBE_EXECUTABLE = "libffprobe.so"
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val MAX_CAPTURED_OUTPUT_CHARS = 2 * 1024 * 1024
        private const val MINIMUM_FREE_SPACE_RESERVE = 32L * 1024L * 1024L
    }
}
