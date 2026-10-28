package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.NativeUrlCore
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Accepts a single, standard YouTube video URL only. The validator deliberately rejects
 * look-alike hosts, insecure schemes, authority tricks, non-standard ports, and playlist-only URLs.
 */
object YouTubeUrlValidator {
    private const val MAX_URL_LENGTH = 4096

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
        if (value.isEmpty() || value.length > MAX_URL_LENGTH || value.any(Char::isWhitespace)) {
            return null
        }

        // The Rust core uses a bounded, allocation-light parser for ordinary ASCII share links.
        // If its library is unavailable, or input contains non-ASCII text, retain the JVM parser.
        val nativeResult = NativeUrlCore.tryNormalize(value)
        if (nativeResult != null) return nativeResult.normalizedUrl

        return normalizeWithJvm(value)
    }

    private fun normalizeWithJvm(value: String): String? {
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null || (uri.port != -1 && uri.port != 443)) return null
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
