package com.ridthewann.youtubeconvert

import android.app.Application
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.ridthewann.youtubeconvert.core.NativeMediaCompatibility
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YoutubeConvertApplication : Application() {
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

        if (!NativeMediaCompatibility.supportsBundledFfmpeg(detectedPageSize)) {
            engineError = NativeMediaCompatibility.userFacingMessage(detectedPageSize)
            Log.w(TAG, "Native media conversion disabled: $engineError")
            return
        }

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            engineReady = true
        } catch (error: Exception) {
            engineError = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Media engine initialization failed", error)
        }
    }

    companion object {
        private const val TAG = "YoutubeConvert"
    }
}
