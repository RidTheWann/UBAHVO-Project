package com.ridthewann.youtubeconvert

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YoutubeConvertApplication : Application() {
    @Volatile
    var engineReady: Boolean = false
        private set

    @Volatile
    var engineError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
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
