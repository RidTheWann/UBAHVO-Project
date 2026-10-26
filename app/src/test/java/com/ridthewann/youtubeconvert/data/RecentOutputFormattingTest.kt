package com.ridthewann.youtubeconvert.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class RecentOutputFormattingTest {
    @Test
    fun formatsBytesAndKilobytes() {
        assertEquals("0 B", RecentOutputFormatting.formatSize(0))
        assertEquals("512 B", RecentOutputFormatting.formatSize(512))
        assertEquals("1.0 KB", RecentOutputFormatting.formatSize(1024))
    }

    @Test
    fun formatsMegabytesAndGigabytes() {
        assertEquals("1.0 MB", RecentOutputFormatting.formatSize(1024L * 1024L))
        assertEquals("1.00 GB", RecentOutputFormatting.formatSize(1024L * 1024L * 1024L))
    }

    @Test
    fun negativeSizeIsClampedToZero() {
        assertEquals("0 B", RecentOutputFormatting.formatSize(-10))
    }

    @Test
    fun missingTimestampHasFallbackText() {
        assertEquals(
            "Tanggal tidak tersedia",
            RecentOutputFormatting.formatDateAdded(0L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun formatsTimestampInRequestedTimeZone() {
        assertEquals(
            "01 Jan 1970, 07:00",
            RecentOutputFormatting.formatDateAdded(1L, ZoneId.of("Asia/Jakarta"))
        )
    }
}
