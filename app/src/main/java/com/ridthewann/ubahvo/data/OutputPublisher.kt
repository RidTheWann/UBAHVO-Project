package com.ridthewann.ubahvo.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

class OutputPublisher(private val context: Context) {
    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
    }

    fun publish(
        source: File,
        mimeType: String,
        cancellationCheck: () -> Unit = {}
    ): PublishedOutput {
        cancellationCheck()
        if (!source.isFile || source.length() <= 0L) {
            throw IOException("The converted output file is missing or empty.")
        }

        val displayName = safeDisplayName(source.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/UBAHVO"
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
                outputStream.buffered().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        cancellationCheck()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }

            // Keep the item hidden until every byte has been copied and cancellation was checked.
            cancellationCheck()
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            val updatedRows = resolver.update(uri, publishValues, null, null)
            if (updatedRows != 1) {
                throw IOException("Android could not publish the completed output file.")
            }
            cancellationCheck()

            return PublishedOutput(uri, displayName, mimeType)
        } catch (error: Exception) {
            // Also removes the row if cancellation arrives after IS_PENDING was cleared.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    /** Delete a just-published output if cancellation wins the race immediately after publication. */
    fun discard(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /**
     * Returns the latest app-owned files from the UBAHVO folder and the previous YoutubeConvert folder.
     * This avoids showing unrelated files another app may place in a similarly named folder.
     */
    fun recentOutputs(limit: Int = DEFAULT_HISTORY_LIMIT): List<RecentOutput> {
        val safeLimit = limit.coerceIn(1, MAX_HISTORY_LIMIT)
        // Search both the new folder and the legacy folder so existing app-owned files
        // remain visible in history after the branding change.
        val relativePaths = listOf(
            "${Environment.DIRECTORY_DOWNLOADS}/UBAHVO",
            "${Environment.DIRECTORY_DOWNLOADS}/YoutubeConvert"
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val pathSelection = relativePaths.joinToString(
            separator = " OR ",
            prefix = "(",
            postfix = ")"
        ) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        }
        val selection = pathSelection +
            " AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND " +
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} IN (?, ?)"
        val selectionArgs = relativePaths.flatMap { path ->
            listOf(path, "$path/", "$path/%")
        }.toMutableList().apply {
            add(context.packageName)
            // The package ID changed in beta.8; keep old-package exports discoverable where Android permits.
            add(LEGACY_APPLICATION_ID)
        }.toTypedArray()
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
        private const val LEGACY_APPLICATION_ID = "com.ridthewann.youtubeconvert"
    }
}

data class PublishedOutput(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)
