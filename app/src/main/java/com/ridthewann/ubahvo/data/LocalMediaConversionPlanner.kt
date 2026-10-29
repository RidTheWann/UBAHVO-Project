package com.ridthewann.ubahvo.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.ridthewann.ubahvo.core.OutputMode

internal data class LocalMediaProbeInfo(
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val durationSeconds: Double?,
    val videoStreamIndex: Int? = null,
    val audioStreamIndex: Int? = null
)

/** Pure conversion planning: no subprocess execution and no shell interpolation. */
internal object LocalMediaConversionPlanner {
    private val objectMapper = ObjectMapper()
    private val encoderLine = Regex("^\\s*[VAS.][A-Z.]{5}\\s+([A-Za-z0-9_]+)\\s")

    fun parseProbeJson(output: String): LocalMediaProbeInfo {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        require(start >= 0 && end > start) { "The media probe did not return valid JSON." }
        val root = objectMapper.readTree(output.substring(start, end + 1))
        var hasVideo = false
        var hasAudio = false
        var videoStreamIndex: Int? = null
        var audioStreamIndex: Int? = null
        val streams = root.path("streams")
        if (streams.isArray) {
            for (stream in streams) {
                val streamIndex = stream.path("index").asInt(-1).takeIf { it >= 0 }
                when (stream.path("codec_type").asText()) {
                    "audio" -> {
                        hasAudio = true
                        if (audioStreamIndex == null) audioStreamIndex = streamIndex
                    }
                    "video" -> {
                        val attachedPicture = stream.path("disposition").path("attached_pic").asInt(0) == 1
                        if (!attachedPicture) {
                            hasVideo = true
                            if (videoStreamIndex == null) videoStreamIndex = streamIndex
                        }
                    }
                }
            }
        }
        require(hasVideo || hasAudio) {
            "The selected file does not contain an audio or video stream."
        }
        val duration = root.path("format").path("duration").asText()
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
        return LocalMediaProbeInfo(hasVideo, hasAudio, duration, videoStreamIndex, audioStreamIndex)
    }

    fun encoderNames(output: String): Set<String> = output.lineSequence()
        .mapNotNull { encoderLine.find(it)?.groupValues?.getOrNull(1) }
        .toSet()

    fun buildArguments(
        mode: OutputMode,
        media: LocalMediaProbeInfo,
        availableEncoders: Set<String>,
        inputPath: String,
        outputPath: String
    ): List<String> {
        val args = mutableListOf("-hide_banner", "-nostdin", "-y", "-loglevel", "error", "-i", inputPath)
        when (mode) {
            OutputMode.MP3 -> {
                require(media.hasAudio) { "MP3 requires an audio stream." }
                val encoder = when {
                    "libmp3lame" in availableEncoders -> "libmp3lame"
                    "libshine" in availableEncoders -> "libshine"
                    else -> throw IllegalArgumentException("The bundled FFmpeg build has no MP3 encoder.")
                }
                val audioMap = media.audioStreamIndex?.let { "0:$it" } ?: "0:a:0"
                args += listOf("-map", audioMap, "-vn", "-sn", "-dn", "-c:a", encoder)
                if (encoder == "libmp3lame") args += listOf("-q:a", "2")
                else args += listOf("-b:a", "192k")
                args += listOf("-f", "mp3")
            }
            OutputMode.MP4 -> {
                require(media.hasVideo) { "MP4 requires a video stream." }
                val encoder = when {
                    "libx264" in availableEncoders -> "libx264"
                    "mpeg4" in availableEncoders -> "mpeg4"
                    else -> throw IllegalArgumentException(
                        "The bundled FFmpeg build has no supported MP4 video encoder."
                    )
                }
                val videoMap = media.videoStreamIndex?.let { "0:$it" } ?: "0:v:0"
                args += listOf("-map", videoMap)
                if (media.hasAudio) {
                    val audioMap = media.audioStreamIndex?.let { "0:$it" } ?: "0:a:0?"
                    args += listOf("-map", audioMap)
                }
                args += listOf("-sn", "-dn", "-c:v", encoder)
                if (encoder == "libx264") args += listOf("-preset", "veryfast", "-crf", "23")
                else args += listOf("-q:v", "4")
                if (media.hasAudio) {
                    require("aac" in availableEncoders) {
                        "The bundled FFmpeg build has no AAC audio encoder for MP4."
                    }
                    args += listOf("-c:a", "aac", "-b:a", "160k")
                }
                args += listOf("-movflags", "+faststart", "-f", "mp4")
            }
        }
        args += listOf("-progress", "pipe:1", "-stats_period", "0.5", "-nostats", outputPath)
        return args
    }

    fun parseTimestampSeconds(value: String): Double? {
        val parts = value.trim().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toDoubleOrNull() ?: return null
        val minutes = parts[1].toDoubleOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        if (hours < 0 || minutes !in 0.0..59.0 || seconds < 0 || seconds >= 60.0) return null
        return hours * 3600.0 + minutes * 60.0 + seconds
    }

    fun progressPercent(timestamp: String, durationSeconds: Double?): Float? {
        val duration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val elapsed = parseTimestampSeconds(timestamp) ?: return null
        return (15.0 + (elapsed / duration).coerceIn(0.0, 1.0) * 80.0)
            .toFloat().coerceIn(15f, 95f)
    }
}
