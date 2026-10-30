package com.ridthewann.ubahvo.core

import kotlinx.coroutines.CancellationException

/**
 * Keeps native dependency initialization from taking down the launcher activity when a wrapper
 * fails to link on a particular Android build. Fatal VM errors are deliberately not swallowed.
 */
object NativeMediaStartup {
    data class Result(
        val engineReady: Boolean,
        val errorMessage: String? = null,
        val failure: Throwable? = null,
        val failureStage: String? = null
    )

    fun initialize(
        pageSizeBytes: Long?,
        initializeEngine: (setStage: (String) -> Unit) -> Unit
    ): Result {
        if (!NativeMediaCompatibility.supportsBundledFfmpeg(pageSizeBytes)) {
            return Result(
                engineReady = false,
                errorMessage = NativeMediaCompatibility.userFacingMessage(pageSizeBytes),
                failureStage = "Pemeriksaan ukuran halaman memori"
            )
        }

        var stage = "Persiapan komponen media"
        return try {
            initializeEngine { currentStage -> stage = currentStage }
            Result(engineReady = true)
        } catch (error: LinkageError) {
            Result(
                engineReady = false,
                errorMessage = "$LINKAGE_FAILURE_MESSAGE\nTahap terakhir: $stage.",
                failure = error,
                failureStage = stage
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // Native wrappers and reflection-based ZIP registries can fail with Errors that are
            // neither LinkageError nor Exception (for example AssertionError). Keep those
            // non-fatal initialization failures from escaping the startup coroutine.
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            Result(
                engineReady = false,
                errorMessage = "$INITIALIZATION_FAILURE_MESSAGE\nTahap terakhir: $stage.",
                failure = error,
                failureStage = stage
            )
        }
    }

    private const val LINKAGE_FAILURE_MESSAGE =
        "Komponen media tidak cocok dengan perangkat ini. Aplikasi tetap bisa dibuka, tetapi konversi dinonaktifkan sampai ada pembaruan kompatibilitas."

    private const val INITIALIZATION_FAILURE_MESSAGE =
        "Komponen media gagal disiapkan. Aplikasi tetap bisa dibuka, tetapi konversi dinonaktifkan. Coba lagi setelah pembaruan aplikasi."
}
