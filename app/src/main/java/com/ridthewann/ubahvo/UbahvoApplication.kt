package com.ridthewann.ubahvo

import android.app.Application
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.ridthewann.ubahvo.core.NativeMediaStartup
import com.ridthewann.ubahvo.data.NativeRuntimeRepair
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

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

    override fun onCreate() {
        super.onCreate()

        val detectedPageSize = runCatching {
            Os.sysconf(OsConstants._SC_PAGE_SIZE)
        }.getOrNull()
        pageSizeBytes = detectedPageSize

        // Native dependency or JNI linkage failures must not prevent the main UI from opening.
        // NativeMediaStartup skips all engine setup on unverified memory page sizes and converts
        // recoverable Kotlin/JVM initialization failures into a disabled-engine state.
        val result = NativeMediaStartup.initialize(detectedPageSize) {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)

            // FFmpeg 0.18.1 omits this shared dependency from its private usr/lib payload.
            // Stage the ABI-matched runtime where the wrapper's LD_LIBRARY_PATH can find it.
            NativeRuntimeRepair.ensureFfmpegCppRuntime(this)
        }

        engineReady = result.engineReady
        engineError = result.errorMessage

        when {
            result.failure is LinkageError -> {
                Log.e(
                    TAG,
                    "Media engine linkage failed; app startup will continue without conversion",
                    result.failure
                )
            }
            result.failure != null -> {
                Log.e(TAG, "Media engine initialization failed; conversion is disabled", result.failure)
            }
            !result.engineReady -> {
                Log.w(TAG, "Native media conversion disabled: " + result.errorMessage)
            }
        }
    }

    companion object {
        private const val TAG = "UBAHVO"
    }
}
