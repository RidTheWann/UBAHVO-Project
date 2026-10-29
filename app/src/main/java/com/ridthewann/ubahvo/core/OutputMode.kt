package com.ridthewann.ubahvo.core

enum class OutputMode(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    MP4("Video MP4", "mp4", "video/mp4"),
    MP3("Audio MP3", "mp3", "audio/mpeg")
}

enum class VideoQuality(val label: String, val maxHeight: Int) {
    P4320("8K (4320p)", 4320),
    P2160("4K (2160p)", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360)
}
