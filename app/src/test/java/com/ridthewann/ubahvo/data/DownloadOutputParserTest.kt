package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadOutputParserTest {
    @Test
    fun extractsTitleFromDedicatedMarker() {
        assertEquals(
            "A title: with punctuation",
            DownloadOutputParser.videoTitleFromLine("UBAHVO_TITLE:A title: with punctuation")
        )
    }

    @Test
    fun trimsTitleAndRejectsAnEmptyMarker() {
        assertEquals("A title", DownloadOutputParser.videoTitleFromLine("UBAHVO_TITLE:  A title  "))
        assertNull(DownloadOutputParser.videoTitleFromLine("UBAHVO_TITLE:   "))
    }

    @Test
    fun ignoresOrdinaryYtDlpOutput() {
        assertNull(DownloadOutputParser.videoTitleFromLine("[download] 12.3% of 8.00MiB"))
    }
}
