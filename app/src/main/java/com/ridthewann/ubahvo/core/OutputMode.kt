package com.ridthewann.ubahvo.core

enum class OutputMode(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    MP4("Video MP4", "mp4", "video/mp4"),
    MP3("Audio MP3", "mp3", "audio/mpeg")
}

enum class VideoQuality(val label: String, val maxHeight: Int?) {
    BEST("Terbaik", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360)
}
