package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.OutputMode

/**
 * Extracts the title marker emitted by yt-dlp's --print option from the same run that downloads
 * the media. This avoids starting a separate metadata-only yt-dlp process before every download.
 */
internal object DownloadOutputParser {
    const val TITLE_PREFIX = "UBAHVO_TITLE:"

    fun videoTitleFromLine(line: String): String? {
        if (!line.startsWith(TITLE_PREFIX)) return null
        return line.removePrefix(TITLE_PREFIX)
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Converts yt-dlp's extractor/postprocessor output into useful early UI stages.
     * Extraction has no percentage progress yet, so these messages keep the UI responsive-looking
     * without pretending that a transfer has already started.
     */
    fun stageMessageFromLine(line: String, mode: OutputMode): String? {
        val normalized = line.trim().lowercase()
        return when {
            normalized.startsWith("extracting url:") ||
                normalized.contains("extracting url: https://") ->
                "Membuka tautan sumber…"

            normalized.contains("player api json") ||
                normalized.contains("downloading player api") ||
                normalized.contains("downloading m3u8 information") ||
                normalized.contains("downloading dash manifest") ||
                normalized.contains("downloading mpd manifest") ->
                "Menganalisis format dan kualitas yang tersedia…"

            normalized.contains("downloading webpage") ||
                normalized.contains("downloading api json") ||
                normalized.contains("downloading player") ||
                normalized.contains("downloading innertube") ->
                "Mengambil informasi video dari sumber…"

            normalized.startsWith("[info]") &&
                normalized.contains("downloading") &&
                normalized.contains("format") ->
                "Format ditemukan — menyiapkan unduhan…"

            normalized.startsWith("[download] destination:") ->
                if (mode == OutputMode.MP3) "Mulai mengunduh audio…" else "Mulai mengunduh video…"

            normalized.startsWith("[merger]") ->
                "Menggabungkan video dan audio…"

            normalized.startsWith("[videoremuxer]") ||
                normalized.startsWith("[fixupm3u8]") ||
                normalized.contains("remuxing video") ->
                if (mode == OutputMode.MP3) "Menyiapkan file audio…" else "Menyiapkan container MP4…"

            else -> null
        }
    }
}
