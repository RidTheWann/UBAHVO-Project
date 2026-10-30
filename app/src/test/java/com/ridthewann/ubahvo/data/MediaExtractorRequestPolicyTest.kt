package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaExtractorRequestPolicyTest {
    @Test
    fun appliesTikTokBrowserUserAgentToShortAndCanonicalHosts() {
        val urls = listOf(
            "https://vt.tiktok.com/ZSgWpeQKW/",
            "https://vm.tiktok.com/ZMabc123/",
            "https://www.tiktok.com/@creator/video/1234567890",
            "https://tiktok.com/@creator/video/1234567890",
            "https://www.tiktokv.com/share/video/1234567890"
        )

        urls.forEach { url ->
            assertEquals(
                "TikTok request policy should apply to $url",
                MediaExtractorRequestPolicy.TIKTOK_USER_AGENT,
                MediaExtractorRequestPolicy.userAgentFor(url)
            )
        }
    }

    @Test
    fun doesNotChangeUserAgentForOtherOrLookalikeHosts() {
        val urls = listOf(
            "https://instagram.com/reel/ABC123/",
            "https://facebook.com/watch/?v=123",
            "https://x.com/creator/status/123",
            "https://tiktok.com.example.org/video/123",
            "https://attacker-tiktok.com/video/123",
            "https://example.org/video.mp4",
            "http://www.tiktok.com/@creator/video/1234567890",
            "not a url"
        )

        urls.forEach { url ->
            assertNull(
                "TikTok request policy must not apply to $url",
                MediaExtractorRequestPolicy.userAgentFor(url)
            )
        }
    }
}
