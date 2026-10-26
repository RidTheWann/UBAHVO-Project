package com.ridthewann.ubahvo.data

import android.net.Uri
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RecentOutput(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long
)

object RecentOutputFormatting {
    private val dateFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    fun formatSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        return when {
            safeBytes < 1024L -> "$safeBytes B"
            safeBytes < 1024L * 1024L ->
                String.format(Locale.ROOT, "%.1f KB", safeBytes / 1024.0)
            safeBytes < 1024L * 1024L * 1024L ->
                String.format(Locale.ROOT, "%.1f MB", safeBytes / (1024.0 * 1024.0))
            else ->
                String.format(Locale.ROOT, "%.2f GB", safeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDateAdded(epochSeconds: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochSeconds <= 0L) return "Tanggal tidak tersedia"
        return try {
            dateFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(zoneId))
        } catch (_: DateTimeException) {
            "Tanggal tidak tersedia"
        }
    }
}
