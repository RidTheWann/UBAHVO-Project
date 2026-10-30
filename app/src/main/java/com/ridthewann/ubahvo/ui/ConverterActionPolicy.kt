package com.ridthewann.ubahvo.ui

import com.ridthewann.ubahvo.core.InputSource
import com.ridthewann.ubahvo.core.OutputMode

/**
 * Decides whether the primary save action may start.
 *
 * The media engine is deliberately initialized lazily on the first MP4/MP3 request.
 * Therefore engineReady is expected to be false on the initial screen. The compatibility
 * gate (engineSupported), not initialization completion, is the correct precondition here.
 */
internal object ConverterActionPolicy {
    fun canStart(
        state: ConverterUiState,
        hasLocalFile: Boolean = state.localFileUri != null
    ): Boolean {
        val mediaEngineAvailable = state.engineSupported ||
            (state.inputSource == InputSource.LINK && state.mode == OutputMode.IMAGE)
        val inputAvailable = when (state.inputSource) {
            InputSource.LINK -> state.url.isNotBlank()
            InputSource.LOCAL_FILE -> hasLocalFile
        }

        return mediaEngineAvailable &&
            inputAvailable &&
            !state.isBusy &&
            !state.isUpdatingYtDlp
    }
}
