package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.NativeUrlCore
import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Validates HTTPS links for media sources supported by yt-dlp.
 *
 * This is a safety/syntax preflight, not a promise that every website or URL has an extractor.
 * Site-specific extraction remains the responsibility of yt-dlp and changes as websites evolve.
 */
object MediaUrlValidator {
    private const val MAX_URL_LENGTH = 4096
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private val numericLabel = Regex("[0-9]+")
    private val hexLabel = Regex("0[xX][0-9A-Fa-f]+")

    fun normalize(input: String): String? {
        val value = input.trim()
        if (value.isEmpty() || value.length > MAX_URL_LENGTH ||
            value.any { it.isWhitespace() || it.isISOControl() }
        ) {
            return null
        }

        // The Rust/C++ fast path handles ordinary ASCII HTTPS links. Non-ASCII URLs use the
        // JVM parser so internationalized domain names can be converted to their ASCII form.
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

        val authority = uri.rawAuthority ?: return null
        if (authority.contains('@') || authority.contains('[') || authority.contains(']') ||
            authority.contains('\\') || authority.endsWith("..")
        ) {
            return null
        }

        val colon = authority.lastIndexOf(':')
        val explicitPort = colon >= 0
        val rawHost = if (explicitPort) {
            if (authority.indexOf(':') != colon || authority.substring(colon + 1) != "443") {
                return null
            }
            authority.substring(0, colon)
        } else {
            authority
        }
        if (rawHost.isEmpty()) return null

        val hostWithTrailingDot = rawHost
        val hostWithoutTrailingDot = hostWithTrailingDot.removeSuffix(".")
        if (hostWithoutTrailingDot.isEmpty() || hostWithoutTrailingDot.endsWith(".")) return null

        val host = try {
            IDN.toASCII(hostWithoutTrailingDot, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!isPublicDnsHost(host)) return null
        if (isYouTubeHost(host) && !isSupportedSingleYouTubeUrl(uri, host)) return null

        // Rebuild the authority with the ASCII/IDNA host while preserving the encoded path/query.
        return buildString {
            append("https://")
            append(host)
            if (explicitPort) append(":443")
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?'); append(it) }
            uri.rawFragment?.let { append('#'); append(it) }
        }
    }

    private fun isPublicDnsHost(host: String): Boolean {
        if (host.length > 253 || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
            return false
        }

        if (host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".lan") || host.endsWith(".home.arpa") ||
            host.endsWith(".test") || host.endsWith(".invalid")
        ) {
            return false
        }

        val labels = host.split('.')
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 ||
                    !label.first().isLetterOrDigit() || !label.last().isLetterOrDigit() ||
                    label.any { !it.isLetterOrDigit() && it != '-' }
            }
        ) {
            return false
        }

        // Reject dotted-decimal and common hexadecimal IPv4 literal spellings. The app accepts
        // DNS hostnames only, avoiding direct links to loopback/private-address endpoints.
        if (labels.all { numericLabel.matches(it) } ||
            labels.size > 1 && labels.all { numericLabel.matches(it) || hexLabel.matches(it) }
        ) {
            return false
        }
        return true
    }

    private fun isYouTubeHost(host: String): Boolean = host == "youtube.com" ||
        host == "www.youtube.com" || host == "m.youtube.com" ||
        host == "music.youtube.com" || host == "youtu.be"

    private fun isSupportedSingleYouTubeUrl(uri: URI, host: String): Boolean {
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> uri.rawQuery.orEmpty()
                .split('&')
                .firstOrNull { it.substringBefore('=') == "v" }
                ?.substringAfter('=', "")
                ?.let(::decodeQueryValue)
            segments.firstOrNull() in setOf("shorts", "live", "embed", "v") ->
                segments.getOrNull(1)
            else -> null
        }
        return videoId != null && videoIdPattern.matches(videoId)
    }

    private fun decodeQueryValue(value: String): String {
        return try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
