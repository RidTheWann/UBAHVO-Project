package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.OutputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaConversionPlannerTest {
    @Test
    fun parsesAudioVideoStreamsAndDuration() {
        val info = LocalMediaConversionPlanner.parseProbeJson(
            """{"format":{"duration":"20.5"},"streams":[{"codec_type":"video","disposition":{"attached_pic":0}},{"codec_type":"audio","disposition":{"attached_pic":0}}]}"""
        )
        assertTrue(info.hasVideo)
        assertTrue(info.hasAudio)
        assertEquals(20.5, info.durationSeconds!!, 0.001)
    }

    @Test
    fun ignoresAlbumArtStreamWhenDetectingVideo() {
        val info = LocalMediaConversionPlanner.parseProbeJson(
            """{"format":{"duration":"8"},"streams":[{"codec_type":"video","disposition":{"attached_pic":1}},{"codec_type":"audio","disposition":{"attached_pic":0}}]}"""
        )
        assertFalse(info.hasVideo)
        assertTrue(info.hasAudio)
    }

    @Test
    fun selectsRealVideoStreamInsteadOfAttachedCoverArt() {
        val info = LocalMediaConversionPlanner.parseProbeJson(
            """{"format":{"duration":"12"},"streams":[{"index":0,"codec_type":"video","disposition":{"attached_pic":1}},{"index":1,"codec_type":"audio","disposition":{"attached_pic":0}},{"index":2,"codec_type":"video","disposition":{"attached_pic":0}}]}"""
        )
        assertTrue(info.hasVideo)
        assertTrue(info.hasAudio)
        assertEquals(2, info.videoStreamIndex)
        assertEquals(1, info.audioStreamIndex)

        val args = LocalMediaConversionPlanner.buildArguments(
            OutputMode.MP4,
            info,
            setOf("libx264", "aac"),
            "/private/source.mkv",
            "/private/output.mp4"
        )
        assertTrue(args.contains("0:2"))
        assertTrue(args.contains("0:1"))
        assertFalse(args.contains("0:v:0"))
    }

    @Test
    fun rejectsProbeOutputWithoutMediaStreams() {
        try {
            LocalMediaConversionPlanner.parseProbeJson("""{"format":{"duration":"4"},"streams":[]}""")
            throw AssertionError("Expected empty streams to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("audio or video"))
        }
    }

    @Test
    fun parsesEncoderNamesFromFfmpegOutput() {
        val encoders = LocalMediaConversionPlanner.encoderNames(
            """
              V..... libx264        H.264 / AVC / MPEG-4 AVC
              V..... mpeg4          MPEG-4 part 2
              A..... aac            AAC (Advanced Audio Coding)
              A....D libmp3lame      MP3 (MPEG audio layer 3)
            """.trimIndent()
        )
        assertTrue("libx264" in encoders)
        assertTrue("mpeg4" in encoders)
        assertTrue("aac" in encoders)
        assertTrue("libmp3lame" in encoders)
    }

    @Test
    fun plansMp4WithH264AndAacWhenEncodersExist() {
        val args = LocalMediaConversionPlanner.buildArguments(
            OutputMode.MP4,
            LocalMediaProbeInfo(true, true, 15.0),
            setOf("libx264", "aac"),
            "/private/source.mp4",
            "/private/output.mp4"
        )
        assertTrue(args.contains("libx264"))
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("+faststart"))
        assertTrue(args.contains("/private/output.mp4"))
    }

    @Test
    fun fallsBackToMpeg4IfH264IsNotAvailable() {
        val args = LocalMediaConversionPlanner.buildArguments(
            OutputMode.MP4,
            LocalMediaProbeInfo(true, false, null),
            setOf("mpeg4"),
            "/private/source.avi",
            "/private/output.mp4"
        )
        assertTrue(args.contains("mpeg4"))
        assertFalse(args.contains("libx264"))
    }

    @Test
    fun rejectsMismatchedInputStreams() {
        try {
            LocalMediaConversionPlanner.buildArguments(
                OutputMode.MP4, LocalMediaProbeInfo(false, true, 4.0),
                setOf("libx264"), "input", "output"
            )
            throw AssertionError("Expected MP4 planning to reject audio-only input")
        } catch (expected: IllegalArgumentException) {
            assertEquals("MP4 requires a video stream.", expected.message)
        }

        try {
            LocalMediaConversionPlanner.buildArguments(
                OutputMode.MP3, LocalMediaProbeInfo(true, false, 4.0),
                setOf("libmp3lame"), "input", "output"
            )
            throw AssertionError("Expected MP3 planning to reject silent video")
        } catch (expected: IllegalArgumentException) {
            assertEquals("MP3 requires an audio stream.", expected.message)
        }
    }

    @Test
    fun rejectsOutputWhenRequiredEncoderIsAbsent() {
        try {
            LocalMediaConversionPlanner.buildArguments(
                OutputMode.MP3, LocalMediaProbeInfo(false, true, 4.0),
                setOf("aac"), "input", "output"
            )
            throw AssertionError("Expected a missing MP3 encoder to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("no MP3 encoder"))
        }
    }

    @Test
    fun computesBoundedProgressOnlyWhenDurationIsKnown() {
        assertEquals(5.5, LocalMediaConversionPlanner.parseTimestampSeconds("00:00:05.500")!!, 0.001)
        assertNull(LocalMediaConversionPlanner.progressPercent("00:00:05.000", null))
        assertEquals(55f, LocalMediaConversionPlanner.progressPercent("00:00:05.000", 10.0)!!, 0.1f)
        assertEquals(95f, LocalMediaConversionPlanner.progressPercent("00:00:15.000", 10.0)!!, 0.1f)
    }
}
