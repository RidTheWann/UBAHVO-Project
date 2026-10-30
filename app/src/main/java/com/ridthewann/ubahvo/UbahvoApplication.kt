package com.ridthewann.ubahvo

import android.app.Application
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.ridthewann.ubahvo.core.NativeMediaCompatibility
import com.ridthewann.ubahvo.core.NativeMediaStartup
import com.ridthewann.ubahvo.data.NativeRuntimeRepair
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StartupPreparationResult(
    val engineReady: Boolean,
    val errorMessage: String?,
    val componentMessage: String
)

class UbahvoApplication : Application() {
    @Volatile
    var engineReady: Boolean = false
        private set

    /** True when this device passed the conservative native-runtime compatibility gate. */
    @Volatile
    var engineSupported: Boolean = false
        private set

    @Volatile
    var engineError: String? = null
        private set

    @Volatile
    var pageSizeBytes: Long? = null
        private set

    @Volatile
    private var compatibilityChecked = false

    @Volatile
    private var initializationComplete = false

    private val initializationLock = Any()

    override fun onCreate() {
        super.onCreate()
        // Keep process startup lightweight. Native packages are extracted only when the user
        // first requests MP4/MP3 conversion, not immediately after the welcome animation.
    }

    /**
     * Performs only the cheap compatibility check needed to show the app UI. It deliberately
     * does not unpack Python/FFmpeg or load the native media wrappers during Activity startup.
     */
    suspend fun prepareStartup(onStage: (String) -> Unit): StartupPreparationResult =
        withContext(Dispatchers.IO) {
            ensureCompatibilityChecked(onStage)
            StartupPreparationResult(
                engineReady = engineReady,
                errorMessage = engineError,
                componentMessage = when {
                    engineReady -> "Komponen media siap."
                    !engineSupported -> engineError
                        ?: "Konversi dinonaktifkan pada perangkat yang belum kompatibel."
                    else -> "Komponen media akan disiapkan saat konversi pertama."
                }
            )
        }

    /**
     * Performs the expensive media setup on the first explicit MP4/MP3 request.
     *
     * If native/JVM initialization fails, the engine is disabled for this process and the
     * caller receives a normal unavailable result. Fatal VM conditions are never swallowed.
     */
    suspend fun ensureMediaEngineInitialized(
        onStage: (String) -> Unit
    ): StartupPreparationResult = withContext(Dispatchers.IO) {
        ensureCompatibilityChecked(onStage)
        if (!engineSupported) {
            return@withContext StartupPreparationResult(
                engineReady = false,
                errorMessage = engineError ?: NativeMediaCompatibility.userFacingMessage(pageSizeBytes),
                componentMessage = "Komponen media tidak kompatibel dengan perangkat ini."
            )
        }

        synchronized(initializationLock) {
            if (!initializationComplete) {
                val result = NativeMediaStartup.initialize(pageSizeBytes) { setStage ->
                    setStage("Inisialisasi yt-dlp")
                    onStage("Menyiapkan yt-dlp dan runtime Python…")
                    YoutubeDL.getInstance().init(this@UbahvoApplication)

                    setStage("Inisialisasi FFmpeg")
                    onStage("Menyiapkan komponen FFmpeg…")
                    FFmpeg.getInstance().init(this@UbahvoApplication)

                    setStage("Perbaikan runtime C++ FFmpeg")
                    onStage("Memverifikasi runtime media…")
                    NativeRuntimeRepair.ensureFfmpegCppRuntime(this@UbahvoApplication)
                }

                engineReady = result.engineReady
                engineSupported = result.engineReady
                engineError = result.errorMessage
                when {
                    result.failure is LinkageError -> Log.e(
                        TAG,
                        "Media engine linkage failed; conversion is disabled for this process",
                        result.failure
                    )
                    result.failure != null -> Log.e(
                        TAG,
                        "Media engine initialization failed; conversion is disabled for this process",
                        result.failure
                    )
                    !result.engineReady -> Log.w(
                        TAG,
                        "Native media conversion disabled: " + result.errorMessage
                    )
                }
                // Do not repeatedly execute a partially failed native initializer in the same
                // process. A fresh process after an app/runtime update can retry safely.
                initializationComplete = true
            }
        }

        StartupPreparationResult(
            engineReady = engineReady,
            errorMessage = engineError,
            componentMessage = if (engineReady) {
                "Komponen media siap."
            } else {
                engineError ?: "Komponen media gagal disiapkan."
            }
        )
    }

    private fun ensureCompatibilityChecked(onStage: (String) -> Unit) {
        if (compatibilityChecked) return
        synchronized(initializationLock) {
            if (compatibilityChecked) return
            onStage("Memeriksa kompatibilitas perangkat…")
            val detectedPageSize = try {
                Os.sysconf(OsConstants._SC_PAGE_SIZE)
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                Log.w(TAG, "Could not read the device memory page size; media conversion will remain disabled", error)
                null
            }
            pageSizeBytes = detectedPageSize
            engineSupported = NativeMediaCompatibility.supportsBundledFfmpeg(detectedPageSize)
            if (!engineSupported) {
                engineError = NativeMediaCompatibility.userFacingMessage(detectedPageSize)
                initializationComplete = true
                Log.w(TAG, "Native media conversion disabled: " + engineError)
            }
            compatibilityChecked = true
        }
    }

    /**
     * Leaves the non-native app usable if an unexpected startup-stage failure escapes a
     * guarded operation. The media engine stays disabled until a later app process.
     */
    fun markMediaEngineUnavailable(error: Throwable) {
        engineReady = false
        engineSupported = false
        engineError = "Komponen media gagal disiapkan. Aplikasi tetap bisa dibuka, tetapi fitur MP4/MP3 dinonaktifkan sampai aplikasi dibuka ulang setelah pembaruan."
        compatibilityChecked = true
        initializationComplete = true
        Log.e(TAG, "Media engine marked unavailable", error)
    }

    companion object {
        private const val TAG = "UBAHVO"
    }
}
