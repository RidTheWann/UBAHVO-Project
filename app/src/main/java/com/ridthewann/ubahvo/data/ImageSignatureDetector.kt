package com.ridthewann.ubahvo.data

import java.nio.charset.StandardCharsets

internal data class RecognizedImageFormat(val extension: String, val mimeType: String)

/** Identifies common raster image formats by their file signatures, not URL suffixes. */
internal object ImageSignatureDetector {
    fun detect(header: ByteArray): RecognizedImageFormat? {
        if (header.size >= 3 && header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
        ) return RecognizedImageFormat("jpg", "image/jpeg")

        val ascii = String(header, StandardCharsets.US_ASCII)
        if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
        ) return RecognizedImageFormat("png", "image/png")
        if (ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a")) return RecognizedImageFormat("gif", "image/gif")
        if (header.size >= 12 && ascii.startsWith("RIFF") && ascii.substring(8, 12) == "WEBP") {
            return RecognizedImageFormat("webp", "image/webp")
        }
        if (ascii.startsWith("BM")) return RecognizedImageFormat("bmp", "image/bmp")
        if (header.size >= 12 && ascii.substring(4, 8) == "ftyp") {
            val brand = ascii.substring(8, 12)
            if (brand == "avif" || brand == "avis") return RecognizedImageFormat("avif", "image/avif")
            if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")) {
                return RecognizedImageFormat("heic", "image/heic")
            }
        }
        return null
    }
}
