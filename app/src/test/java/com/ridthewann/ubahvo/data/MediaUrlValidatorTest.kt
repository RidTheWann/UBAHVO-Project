package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlValidatorTest {
    @Test
    fun acceptsCommonSupportedMediaSourceUrlShapes() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=abcdefghijk",
            "https://youtu.be/abcdefghijk",
            "https://youtube.com/shorts/abcdefghijk",
            "https://vimeo.com/123456789",
            "https://www.tiktok.com/@creator/video/1234567890",
            "https://www.instagram.com/reel/C0ffee12345/",
            "https://soundcloud.com/artist/track-name",
            "https://x.com/creator/status/1234567890123456789",
            "https://files.example.org/media/demo.mp4",
            "https://youtube.com:443/watch?v=abcdefghijk"
        )

        urls.forEach { url ->
            assertEquals("URL should pass preflight: $url", url, MediaUrlValidator.normalize(url))
        }
    }

    @Test
    fun rejectsUnsafeOrMalformedUrls() {
        val urls = listOf(
            "",
            "http://youtube.com/watch?v=abcdefghijk",
            "ftp://files.example.org/demo.mp4",
            "https://attacker@example.org/media.mp4",
            "https://example.org:8443/media.mp4",
            "https://example.org../media.mp4",
            "https://localhost/media.mp4",
            "https://nas.local/media.mp4",
            "https://127.0.0.1/media.mp4",
            "https://192.168.1.10/media.mp4",
            "https://0x7f.0.0.1/media.mp4",
            "https://example.org/%GG",
            "https://example.org/media file.mp4",
            "https://youtube.com/playlist?list=PL123456789",
            "https://youtube.com/watch?v=short"
        )

        urls.forEach { url ->
            assertNull("URL should fail preflight: $url", MediaUrlValidator.normalize(url))
        }
    }

    @Test
    fun rejectsOversizedUrlsBeforeParsing() {
        val url = "https://files.example.org/media.mp4?x=" + "a".repeat(5000)
        assertNull(MediaUrlValidator.normalize(url))
    }

    @Test
    fun canonicalizesAnInternationalizedHost() {
        assertEquals(
            "https://xn--bcher-kva.example.org/media.mp4",
            MediaUrlValidator.normalize("https://bücher.example.org/media.mp4")
        )
    }
}
