package com.ridthewann.ubahvo.data

import com.ridthewann.ubahvo.core.VideoQuality

/**
 * Produces yt-dlp format expressions for MP4 output.
 *
 * Prefer native MP4 streams when they exist. For high-resolution sources such as YouTube 8K,
 * fall back to the best available video/audio pair regardless of source container; yt-dlp/FFmpeg
 * then remuxes the selected streams into the requested MP4 container without re-encoding.
 */
internal object VideoFormatSelector {
    fun forQuality(quality: VideoQuality): String {
        val heightLimit = "[height<=${quality.maxHeight}]"
        val preferredMp4 =
            "(bestvideo[ext=mp4]$heightLimit+bestaudio[ext=m4a]/best[ext=mp4]$heightLimit)"
        val anyContainer =
            "(bestvideo$heightLimit+bestaudio[ext=m4a]/best$heightLimit)/(bestvideo$heightLimit+bestaudio/best$heightLimit)"
        return "$preferredMp4/$anyContainer"
    }
}
