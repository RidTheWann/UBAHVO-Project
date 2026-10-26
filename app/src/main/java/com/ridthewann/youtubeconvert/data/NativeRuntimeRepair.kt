package com.ridthewann.youtubeconvert.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Repairs the published youtubedl-android FFmpeg payload when it omits libc++_shared.so.
 *
 * The runtime is packaged per ABI from the pinned Android NDK by Gradle, then copied to the
 * exact directory already included in YoutubeDL's LD_LIBRARY_PATH. This is a compatibility
 * mitigation for upstream FFmpeg 0.18.1 issue #369, not a substitute for an upstream fix.
 */
object NativeRuntimeRepair {
    fun ensureFfmpegCppRuntime(context: Context) {
        val packagedRuntime = File(context.applicationInfo.nativeLibraryDir, CPP_RUNTIME_NAME)
        val ffmpegLibraryDirectory = File(
            context.noBackupFilesDir,
            "youtubedl-android/packages/ffmpeg/usr/lib"
        )
        stageLibrary(packagedRuntime, ffmpegLibraryDirectory)
    }

    internal fun stageLibrary(sourceLibrary: File, destinationDirectory: File): File {
        if (!sourceLibrary.isFile || sourceLibrary.length() <= 0L) {
            throw IOException("The packaged Android C++ runtime is missing or empty.")
        }

        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw IOException("Could not create FFmpeg's private library directory.")
        }
        if (!destinationDirectory.isDirectory) {
            throw IOException("FFmpeg's private library path is not a directory.")
        }

        val destination = File(destinationDirectory, CPP_RUNTIME_NAME)
        val temporary = File(destinationDirectory, CPP_RUNTIME_NAME + ".tmp")

        try {
            sourceLibrary.inputStream().buffered().use { input ->
                FileOutputStream(temporary).buffered().use { output ->
                    input.copyTo(output)
                }
            }

            if (temporary.length() != sourceLibrary.length()) {
                throw IOException("The staged Android C++ runtime has an unexpected size.")
            }

            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            if (!destination.isFile || destination.length() != sourceLibrary.length()) {
                throw IOException("Could not verify the staged Android C++ runtime.")
            }
            return destination
        } catch (error: Exception) {
            runCatching { temporary.delete() }
            if (error is IOException) throw error
            throw IOException("Failed to stage the Android C++ runtime for FFmpeg.", error)
        }
    }

    private const val CPP_RUNTIME_NAME = "libc++_shared.so"
}
