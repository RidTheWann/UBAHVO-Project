package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.VideoQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatSelectorTest {
    @Test
    fun eightKQualityCapsEveryVideoFallbackAt4320p() {
        val format = VideoFormatSelector.forQuality(VideoQuality.P4320)

        assertTrue(format.contains("bestvideo[ext=mp4][height<=4320]"))
        assertTrue(format.contains("best[ext=mp4][height<=4320]"))
        assertTrue(format.contains("bestvideo[height<=4320]+bestaudio[ext=m4a]"))
        assertTrue(format.contains("bestvideo[height<=4320]+bestaudio/best[height<=4320]"))
        assertTrue(format.contains("best[height<=4320]"))
        assertFalse(format.contains("height<=2160"))
    }

    @Test
    fun fourKQualityCapsAt2160p() {
        val format = VideoFormatSelector.forQuality(VideoQuality.P2160)

        assertTrue(format.contains("height<=2160"))
        assertFalse(format.contains("height<=4320"))
    }

    @Test
    fun prefersMp4ButFallsBackToOtherSourceContainersForRemux() {
        val format = VideoFormatSelector.forQuality(VideoQuality.P4320)

        assertTrue(format.startsWith("(bestvideo[ext=mp4]"))
        assertTrue(format.contains("/(bestvideo[height<=4320]+bestaudio/best[height<=4320])"))
    }

    @Test
    fun everyQualityIsExplicitlyCappedAndThereIsNoBestOption() {
        assertTrue(VideoQuality.entries.none { it.label.equals("Terbaik", ignoreCase = true) })
        assertTrue(VideoQuality.entries.all { it.maxHeight > 0 })

        val format = VideoFormatSelector.forQuality(VideoQuality.P1080)
        assertTrue(format.contains("height<=1080"))
        assertFalse(format.contains("height<=4320"))
    }
}
