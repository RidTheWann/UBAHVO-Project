package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.OutputMode
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

    @Test
    fun reportsExtractionStagesBeforeDownloadPercentageAppears() {
        assertEquals(
            "Membuka tautan sumber…",
            DownloadOutputParser.stageMessageFromLine(
                "[youtube] Extracting URL: https://www.youtube.com/watch?v=abcdefghijk",
                OutputMode.MP4
            )
        )
        assertEquals(
            "Mengambil informasi video dari sumber…",
            DownloadOutputParser.stageMessageFromLine("[youtube] abc: Downloading webpage", OutputMode.MP4)
        )
        assertEquals(
            "Mengambil informasi video dari sumber…",
            DownloadOutputParser.stageMessageFromLine("[youtube] abc: Downloading android player API JSON", OutputMode.MP4)
        )
    }

    @Test
    fun reportsDownloadAndPostProcessingStages() {
        assertEquals(
            "Menyiapkan format video yang tersedia…",
            DownloadOutputParser.stageMessageFromLine("[info] abc: Downloading 2 format(s): 313+140", OutputMode.MP4)
        )
        assertEquals(
            "Mulai mengunduh video…",
            DownloadOutputParser.stageMessageFromLine("[download] Destination: sample.f313.webm", OutputMode.MP4)
        )
        assertEquals(
            "Menggabungkan video dan audio…",
            DownloadOutputParser.stageMessageFromLine("[Merger] Merging formats into sample.mp4", OutputMode.MP4)
        )
    }

    @Test
    fun ignoresUnrecognizedOutputAsAStage() {
        assertNull(DownloadOutputParser.stageMessageFromLine("[download] 14.2% of 200MiB", OutputMode.MP4))
    }
}
