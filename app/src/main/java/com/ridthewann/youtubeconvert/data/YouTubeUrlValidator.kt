package com.ridthewann.youtubeconvert.data

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Accepts a single, standard YouTube video URL only. The validator deliberately rejects
 * look-alike hosts, insecure schemes, playlists without a video, and arbitrary URLs.
 */
object YouTubeUrlValidator {
    private val supportedHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.any(Char::isWhitespace)) return null

        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removeSuffix(".") ?: return null
        if (host !in supportedHosts) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> {
                uri.rawQuery.orEmpty()
                    .split('&')
                    .firstOrNull { it.substringBefore('=') == "v" }
                    ?.substringAfter('=', "")
                    ?.let(::decodeQueryValue)
            }
            segments.firstOrNull() in setOf("shorts", "live", "embed", "v") -> segments.getOrNull(1)
            else -> null
        }

        if (videoId == null || !videoIdPattern.matches(videoId)) return null
        return uri.toASCIIString()
    }

    private fun decodeQueryValue(value: String): String {
        return try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
