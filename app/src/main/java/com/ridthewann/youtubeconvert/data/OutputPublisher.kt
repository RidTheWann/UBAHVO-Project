package com.ridthewann.youtubeconvert.data

import android.content.ContentUris
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
            // Open the source before the MediaStore stream so both are closed if either side fails.
            source.inputStream().buffered().use { input ->
                val outputStream = resolver.openOutputStream(uri, "w")
                    ?: throw IOException("Android could not open the output stream.")
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

    /**
     * Returns only app-owned files from this app's Downloads/YoutubeConvert directory.
     * This avoids showing unrelated files another app may place in a similarly named folder.
     */
    fun recentOutputs(limit: Int = DEFAULT_HISTORY_LIMIT): List<RecentOutput> {
        val safeLimit = limit.coerceIn(1, MAX_HISTORY_LIMIT)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/YoutubeConvert"
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val selection = "(" +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" +
            ") AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND " +
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?"
        val selectionArgs = arrayOf(
            relativePath,
            "$relativePath/",
            "$relativePath/%",
            context.packageName
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC, " +
            "${MediaStore.MediaColumns._ID} DESC"
        val results = ArrayList<RecentOutput>(safeLimit)

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext() && results.size < safeLimit) {
                val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
                val id = cursor.getLong(idIndex)
                val mimeType = cursor.getString(mimeIndex)?.takeIf { it.isNotBlank() }
                    ?: mimeTypeFor(name)
                val sizeBytes = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                val dateAdded = if (cursor.isNull(dateIndex)) 0L else cursor.getLong(dateIndex)

                results += RecentOutput(
                    uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    displayName = name,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes.coerceAtLeast(0L),
                    dateAddedSeconds = dateAdded.coerceAtLeast(0L)
                )
            }
        }

        return results
    }

    private fun mimeTypeFor(displayName: String): String =
        when (displayName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
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

    companion object {
        private const val DEFAULT_HISTORY_LIMIT = 5
        private const val MAX_HISTORY_LIMIT = 20
    }
}

data class PublishedOutput(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)
