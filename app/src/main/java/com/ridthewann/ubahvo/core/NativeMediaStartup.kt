package com.ridthewann.ubahvo.core

/**
 * Keeps native dependency initialization from taking down the launcher activity when a wrapper
 * fails to link on a particular Android build. Fatal VM errors are deliberately not swallowed.
 */
object NativeMediaStartup {
    data class Result(
        val engineReady: Boolean,
        val errorMessage: String? = null,
        val failure: Throwable? = null
    )

    fun initialize(pageSizeBytes: Long?, initializeEngine: () -> Unit): Result {
        if (!NativeMediaCompatibility.supportsBundledFfmpeg(pageSizeBytes)) {
            return Result(
                engineReady = false,
                errorMessage = NativeMediaCompatibility.userFacingMessage(pageSizeBytes)
            )
        }

        return try {
            initializeEngine()
            Result(engineReady = true)
        } catch (error: LinkageError) {
            Result(
                engineReady = false,
                errorMessage = LINKAGE_FAILURE_MESSAGE,
                failure = error
            )
        } catch (error: Exception) {
            Result(
                engineReady = false,
                errorMessage = INITIALIZATION_FAILURE_MESSAGE,
                failure = error
            )
        }
    }

    private const val LINKAGE_FAILURE_MESSAGE =
        "Komponen media tidak cocok dengan perangkat ini. Aplikasi tetap bisa dibuka, tetapi konversi dinonaktifkan sampai ada pembaruan kompatibilitas."

    private const val INITIALIZATION_FAILURE_MESSAGE =
        "Komponen media gagal disiapkan. Aplikasi tetap bisa dibuka, tetapi konversi dinonaktifkan. Coba lagi setelah pembaruan aplikasi."
}
