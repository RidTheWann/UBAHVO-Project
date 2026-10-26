package com.ridthewann.youtubeconvert.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMediaCompatibilityTest {
    @Test
    fun permitsOnlyTheVerifiedFourKbPageSize() {
        assertTrue(NativeMediaCompatibility.supportsBundledFfmpeg(4096L))
    }

    @Test
    fun rejectsSixteenKbPageSize() {
        assertFalse(NativeMediaCompatibility.supportsBundledFfmpeg(16384L))
    }

    @Test
    fun rejectsUnverifiedEightKbPageSize() {
        assertFalse(NativeMediaCompatibility.supportsBundledFfmpeg(8192L))
    }

    @Test
    fun rejectsUnknownPageSizeConservatively() {
        assertFalse(NativeMediaCompatibility.supportsBundledFfmpeg(null))
    }

    @Test
    fun unsupportedPageSizeGetsClearUserFacingMessage() {
        val message = NativeMediaCompatibility.userFacingMessage(16384L)
        assertTrue(message.contains("16 KB"))
        assertTrue(message.contains("crash native"))
    }

    @Test
    fun unknownPageSizeGetsSafeMessage() {
        assertTrue(NativeMediaCompatibility.userFacingMessage(null).contains("tidak dapat memastikan"))
    }
}
