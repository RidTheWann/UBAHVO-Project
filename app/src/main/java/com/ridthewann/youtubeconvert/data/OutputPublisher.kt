package com.ridthewann.youtubeconvert.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

class OutputPublisher(private val context: Context) {
    fun publish(source: File, mimeType: String): PublishedOutput {
        if (!source.isFile || source.length() <= 0L) {
            throw IOException("The converted output file is missing or empty.")
        }

        val displayName = safeDisplayName(source.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/YoutubeConvert"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android could not create the output file.")

        try {
            val outputStream = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Android could not open the output stream.")
            source.inputStream().buffered().use { input ->
                outputStream.buffered().use { output -> input.copyTo(output) }
            }

            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            val updatedRows = resolver.update(uri, publishValues, null, null)
            if (updatedRows != 1) {
                throw IOException("Android could not publish the completed output file.")
            }

            return PublishedOutput(uri, displayName, mimeType)
        } catch (error: Exception) {
            // Do not leave a broken or permanently pending MediaStore entry behind.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun safeDisplayName(original: String): String {
        val extension = original.substringAfterLast('.', "").lowercase()
        val rawBase = original.substringBeforeLast('.', original)
        val safeBase = rawBase
            .map { char ->
                if (char.isLetterOrDigit() || char in " ._()-[]") char else '_'
            }
            .joinToString("")
            .trim()
            .take(120)
            .ifBlank { "converted_media" }

        return if (extension.isBlank()) safeBase else "$safeBase.$extension"
    }
}

data class PublishedOutput(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)
