package com.ridthewann.ubahvo.ui

import com.ridthewann.ubahvo.core.InputSource
import com.ridthewann.ubahvo.core.OutputMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterActionPolicyTest {
    @Test
    fun firstMediaRequestCanStartBeforeLazyEngineInitialization() {
        val state = ConverterUiState(
            url = "https://www.tiktok.com/@creator/video/1234567890",
            mode = OutputMode.MP4,
            engineSupported = true,
            engineReady = false
        )

        assertTrue(ConverterActionPolicy.canStart(state))
    }

    @Test
    fun audioRequestCanStartBeforeLazyEngineInitialization() {
        val state = ConverterUiState(
            url = "https://www.instagram.com/reel/ABC123/",
            mode = OutputMode.MP3,
            engineSupported = true,
            engineReady = false
        )

        assertTrue(ConverterActionPolicy.canStart(state))
    }

    @Test
    fun publicImageDownloadDoesNotRequireNativeMediaEngine() {
        val state = ConverterUiState(
            url = "https://images.example.org/photo.jpg",
            mode = OutputMode.IMAGE,
            engineSupported = false,
            engineReady = false
        )

        assertTrue(ConverterActionPolicy.canStart(state))
    }

    @Test
    fun unsupportedNativeRuntimeStillBlocksVideoAndAudio() {
        val url = "https://www.facebook.com/watch/?v=123456789"
        assertFalse(
            ConverterActionPolicy.canStart(
                ConverterUiState(url = url, mode = OutputMode.MP4, engineSupported = false)
            )
        )
        assertFalse(
            ConverterActionPolicy.canStart(
                ConverterUiState(url = url, mode = OutputMode.MP3, engineSupported = false)
            )
        )
    }

    @Test
    fun localFileConversionRequiresSupportedEngineAndSelectedFile() {
        val state = ConverterUiState(
            inputSource = InputSource.LOCAL_FILE,
            mode = OutputMode.MP4,
            engineSupported = true
        )

        assertFalse(ConverterActionPolicy.canStart(state))
        assertTrue(ConverterActionPolicy.canStart(state, hasLocalFile = true))
        assertFalse(
            ConverterActionPolicy.canStart(state.copy(engineSupported = false), hasLocalFile = true)
        )
    }

    @Test
    fun missingLinkBusyAndManualUpdateStatesDisableSaveAction() {
        val ready = ConverterUiState(
            url = "https://x.com/creator/status/123456789",
            engineSupported = true
        )

        assertFalse(ConverterActionPolicy.canStart(ready.copy(url = "")))
        assertFalse(ConverterActionPolicy.canStart(ready.copy(isBusy = true)))
        assertFalse(ConverterActionPolicy.canStart(ready.copy(isUpdatingYtDlp = true)))
        assertTrue(ConverterActionPolicy.canStart(ready))
    }
}
