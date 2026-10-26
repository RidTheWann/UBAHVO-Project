package com.ridthewann.youtubeconvert.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlValidatorTest {
    @Test
    fun acceptsStandardWatchUrl() {
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        assertEquals(url, YouTubeUrlValidator.normalize(url))
    }

    @Test
    fun acceptsShortUrl() {
        val url = "https://youtu.be/abcdefghijk"
        assertEquals(url, YouTubeUrlValidator.normalize(url))
    }

    @Test
    fun acceptsShortsUrl() {
        val url = "https://youtube.com/shorts/abcdefghijk"
        assertEquals(url, YouTubeUrlValidator.normalize(url))
    }

    @Test
    fun rejectsLookAlikeHost() {
        assertNull(YouTubeUrlValidator.normalize("https://youtube.com.example.org/watch?v=abcdefghijk"))
    }

    @Test
    fun rejectsInsecureUrl() {
        assertNull(YouTubeUrlValidator.normalize("http://youtube.com/watch?v=abcdefghijk"))
    }

    @Test
    fun rejectsPlaylistOnlyUrl() {
        assertNull(YouTubeUrlValidator.normalize("https://www.youtube.com/playlist?list=PL123456789"))
    }

    @Test
    fun rejectsInvalidVideoId() {
        assertNull(YouTubeUrlValidator.normalize("https://www.youtube.com/watch?v=short"))
    }
}
