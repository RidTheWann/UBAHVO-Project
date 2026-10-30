package com.ridthewann.ubahvo.data

import java.net.URI
import java.util.Locale

/**
 * Selects narrowly scoped HTTP identity hints for extractors with documented site-specific
 * request fingerprint changes. This is not authentication and does not bypass private content.
 */
internal object MediaExtractorRequestPolicy {
    const val TIKTOK_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

    fun userAgentFor(rawUrl: String): String? {
        val host = runCatching {
            URI(rawUrl).host?.lowercase(Locale.ROOT)
        }.getOrNull() ?: return null

        return if (
            host == "tiktok.com" || host.endsWith(".tiktok.com") ||
            host == "tiktokv.com" || host.endsWith(".tiktokv.com")
        ) {
            TIKTOK_USER_AGENT
        } else {
            null
        }
    }
}
