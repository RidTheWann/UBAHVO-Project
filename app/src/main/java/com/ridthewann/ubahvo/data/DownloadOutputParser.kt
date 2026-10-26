package com.ridthewann.ubahvo.data

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
}
