package com.ridthewann.youtubeconvert.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class NativeRuntimeRepairTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagesRuntimeIntoFfmpegLibraryDirectory() {
        val source = temporaryFolder.newFile("source-libc++_shared.so").apply {
            writeBytes(byteArrayOf(1, 3, 5, 7, 9))
        }
        val targetDirectory = File(temporaryFolder.root, "packages/ffmpeg/usr/lib")

        val staged = NativeRuntimeRepair.stageLibrary(source, targetDirectory)

        assertEquals("libc++_shared.so", staged.name)
        assertTrue(staged.isFile)
        assertArrayEquals(source.readBytes(), staged.readBytes())
    }

    @Test
    fun atomicallyReplacesAnExistingRuntime() {
        val source = temporaryFolder.newFile("source-libc++_shared.so").apply {
            writeBytes(byteArrayOf(2, 4, 6, 8))
        }
        val targetDirectory = temporaryFolder.newFolder("usr", "lib")
        File(targetDirectory, "libc++_shared.so").writeBytes(byteArrayOf(0, 0, 0, 0))

        val staged = NativeRuntimeRepair.stageLibrary(source, targetDirectory)

        assertArrayEquals(source.readBytes(), staged.readBytes())
    }

    @Test
    fun rejectsMissingRuntimeSource() {
        val missingSource = File(temporaryFolder.root, "missing-libc++_shared.so")
        val targetDirectory = File(temporaryFolder.root, "usr/lib")

        try {
            NativeRuntimeRepair.stageLibrary(missingSource, targetDirectory)
            throw AssertionError("Expected an IOException for a missing runtime.")
        } catch (_: IOException) {
            // Expected: fail closed rather than allowing FFmpeg to start without its shared runtime.
        }
    }
}
