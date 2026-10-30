package com.ridthewann.ubahvo

import android.app.Application
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.ridthewann.ubahvo.core.NativeMediaStartup
import com.ridthewann.ubahvo.data.NativeRuntimeRepair
import com.ridthewann.ubahvo.data.StableYtDlpUpdater
import com.ridthewann.ubahvo.data.YtDlpAutoUpdatePolicy
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

    @Volatile
    var engineError: String? = null
        private set

    @Volatile
    var pageSizeBytes: Long? = null
        private set

    @Volatile
    private var initializationComplete = false

    private val initializationLock = Any()

    override fun onCreate() {
        super.onCreate()
        // Native startup is deferred so the launcher can display the welcome animation first.
    }

    suspend fun prepareStartup(onStage: (String) -> Unit): StartupPreparationResult =
        withContext(Dispatchers.IO) {
            synchronized(initializationLock) {
                if (!initializationComplete) {
                    onStage("Memeriksa kompatibilitas perangkat…")
                    val detectedPageSize = runCatching {
                        Os.sysconf(OsConstants._SC_PAGE_SIZE)
                    }.getOrNull()
                    pageSizeBytes = detectedPageSize

                    val result = NativeMediaStartup.initialize(detectedPageSize) { setStage ->
                        setStage("Inisialisasi yt-dlp")
                        onStage("Inisialisasi yt-dlp")
                        YoutubeDL.getInstance().init(this@UbahvoApplication)

                        setStage("Inisialisasi FFmpeg")
                        onStage("Inisialisasi FFmpeg")
                        FFmpeg.getInstance().init(this@UbahvoApplication)

                        setStage("Perbaikan runtime C++ FFmpeg")
                        onStage("Menyiapkan runtime media…")
                        NativeRuntimeRepair.ensureFfmpegCppRuntime(this@UbahvoApplication)
                    }

                    engineReady = result.engineReady
                    engineError = result.errorMessage
                    when {
                        result.failure is LinkageError -> Log.e(
                            TAG,
                            "Media engine linkage failed; app startup will continue without conversion",
                            result.failure
                        )
                        result.failure != null -> Log.e(
                            TAG,
                            "Media engine initialization failed; conversion is disabled",
                            result.failure
                        )
                        !result.engineReady -> Log.w(
                            TAG,
                            "Native media conversion disabled: " + result.errorMessage
                        )
                    }
                    initializationComplete = true
                }
            }

            var componentMessage = "Komponen media siap."
            if (!engineReady) {
                componentMessage = engineError ?: "Komponen media tidak tersedia di perangkat ini."
            } else {
                val preferences = getSharedPreferences(AUTO_UPDATE_PREFERENCES, MODE_PRIVATE)
                val nextCheckAt = preferences.getLong(KEY_NEXT_AUTO_UPDATE_AT, 0L)
                if (YtDlpAutoUpdatePolicy.isDue(System.currentTimeMillis(), nextCheckAt)) {
                    onStage("Memeriksa dan memperbarui komponen media…")
                    var succeeded = false
                    try {
                        val result = StableYtDlpUpdater(this@UbahvoApplication).update()
                        succeeded = true
                        val version = result.versionName?.takeIf { it.isNotBlank() }
                            ?: result.version?.takeIf { it.isNotBlank() }
                        componentMessage = if (result.updated) {
                            "Komponen media berhasil diperbarui" +
                                (version?.let { " ke $it" } ?: "") + "."
                        } else {
                            "Komponen media sudah terbaru" +
                                (version?.let { " ($it)" } ?: "") + "."
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "Automatic media-component update failed; existing components are retained", error)
                        componentMessage =
                            "Pembaruan komponen belum berhasil. UBAHVO tetap dibuka dengan komponen yang tersedia."
                    } finally {
                        preferences.edit()
                            .putLong(
                                KEY_NEXT_AUTO_UPDATE_AT,
                                YtDlpAutoUpdatePolicy.nextCheckAt(System.currentTimeMillis(), succeeded)
                            )
                            .apply()
                    }
                } else {
                    onStage("Menyelesaikan persiapan…")
                }
            }

            StartupPreparationResult(
                engineReady = engineReady,
                errorMessage = engineError,
                componentMessage = componentMessage
            )
        }

    companion object {
        private const val TAG = "UBAHVO"
        private const val AUTO_UPDATE_PREFERENCES = "automatic_ytdlp_support_updates"
        private const val KEY_NEXT_AUTO_UPDATE_AT = "next_check_at_ms"
    }
}
