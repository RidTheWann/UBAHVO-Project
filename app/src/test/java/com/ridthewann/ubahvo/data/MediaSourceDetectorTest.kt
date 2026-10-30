package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceDetectorTest {
    @Test
    fun identifiesCommonSocialVideoAndShortShareLinks() {
        val examples = mapOf(
            "https://www.tiktok.com/@creator/video/1234567890" to "TikTok",
            "https://vm.tiktok.com/ZMabc123/" to "TikTok",
            "https://vt.tiktok.com/ZSgWpeQKW/" to "TikTok",
            "https://www.instagram.com/reel/ABC123/" to "Instagram",
            "https://instagr.am/p/ABC123/" to "Instagram",
            "https://www.facebook.com/watch/?v=123456789" to "Facebook",
            "https://fb.watch/AbCdEf/" to "Facebook",
            "https://x.com/creator/status/123456789" to "X / Twitter",
            "https://twitter.com/creator/status/123456789" to "X / Twitter",
            "https://www.threads.net/@creator/post/123456789" to "Threads",
            "https://www.reddit.com/r/videos/comments/abc123/title/" to "Reddit",
            "https://v.redd.it/abc123" to "Reddit",
            "https://pin.it/AbCdEf" to "Pinterest",
            "https://www.snapchat.com/spotlight/abc123" to "Snapchat"
        )

        examples.forEach { (url, expected) ->
            assertEquals(url, expected, MediaSourceDetector.displayName(url))
        }
    }

    @Test
    fun identifiesEstablishedVideoProvidersAndKeepsUnknownSitesGeneric() {
        assertEquals("YouTube", MediaSourceDetector.displayName("https://youtu.be/abcdefghijk"))
        assertEquals("Vimeo", MediaSourceDetector.displayName("https://vimeo.com/12345"))
        assertEquals("Twitch", MediaSourceDetector.displayName("https://clips.twitch.tv/ClipName"))
        assertEquals("SoundCloud", MediaSourceDetector.displayName("https://soundcloud.com/artist/track"))
        assertEquals("sumber media", MediaSourceDetector.displayName("https://cdn.example.org/video.mp4"))
    }

    @Test
    fun doesNotMatchLookalikeDomains() {
        assertEquals("sumber media", MediaSourceDetector.displayName("https://tiktok.com.example.org/video"))
        assertEquals("sumber media", MediaSourceDetector.displayName("https://instagram.com.attacker.example/reel"))
        assertEquals("sumber media", MediaSourceDetector.displayName("https://facebook.co.example/watch"))
    }
}
