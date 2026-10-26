package com.ridthewann.ubahvo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMediaStartupTest {
    @Test
    fun unsupportedPageSizeSkipsNativeInitialization() {
        var initialized = false

        val result = NativeMediaStartup.initialize(16384L) {
            initialized = true
        }

        assertFalse(initialized)
        assertFalse(result.engineReady)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("16 KB"))
        assertNull(result.failure)
        assertEquals("Pemeriksaan ukuran halaman memori", result.failureStage)
    }

    @Test
    fun successfulInitializationEnablesEngine() {
        val result = NativeMediaStartup.initialize(4096L) { _ -> }

        assertTrue(result.engineReady)
        assertNull(result.errorMessage)
        assertNull(result.failure)
        assertNull(result.failureStage)
    }

    @Test
    fun linkageErrorIsReportedWithTheFailingStage() {
        // Match the observed crash: Apache Commons Compress throws an ExceptionInInitializerError
        // when its ZIP extra-field registry cannot instantiate AsiExtraField through reflection.
        val failure = ExceptionInInitializerError(
            RuntimeException(
                "class org.apache.commons.compress.archivers.zip.AsiExtraField is not a concrete class"
            )
        )

        val result = NativeMediaStartup.initialize(4096L) { setStage ->
            setStage("Inisialisasi FFmpeg")
            throw failure
        }

        assertFalse(result.engineReady)
        assertTrue(result.errorMessage!!.contains("Aplikasi tetap bisa dibuka"))
        assertTrue(result.errorMessage!!.contains("Tahap terakhir: Inisialisasi FFmpeg"))
        assertEquals("Inisialisasi FFmpeg", result.failureStage)
        assertSame(failure, result.failure)
    }

    @Test
    fun ordinaryInitializationFailureIsReportedInsteadOfEscaping() {
        val failure = IllegalStateException("test-init-failure")

        val result = NativeMediaStartup.initialize(4096L) {
            it("Inisialisasi yt-dlp")
            throw failure
        }

        assertFalse(result.engineReady)
        assertTrue(result.errorMessage!!.contains("Aplikasi tetap bisa dibuka"))
        assertTrue(result.errorMessage!!.contains("Tahap terakhir: Inisialisasi yt-dlp"))
        assertEquals("Inisialisasi yt-dlp", result.failureStage)
        assertSame(failure, result.failure)
    }
}
