package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicImageMetadataParserTest {
    @Test
    fun parsesOpenGraphImageWhenAttributesAreReorderedAndUsesRelativeResolution() {
        val html = """
            <HTML><HEAD>
              <meta content="//cdn.example.org/wallpaper.webp?token=a&amp;size=full" property='og:image'/>
              <meta content="Sakura &amp; Torii" property="og:title">
            </HEAD></HTML>
        """.trimIndent()

        val metadata = PublicImageMetadataParser.parse(html, "https://www.example.org/post/123")

        assertEquals("Sakura & Torii", metadata?.title)
        assertEquals("https://cdn.example.org/wallpaper.webp?token=a&size=full", metadata?.imageUrls?.first())
    }

    @Test
    fun supportsTwitterImageAndHtmlTitleFallback() {
        val metadata = PublicImageMetadataParser.parse(
            """<html><head><title>Ocean &amp; Sky</title><meta name="twitter:image" content="/images/ocean.jpg"></head></html>""",
            "https://social.example.org/p/abc"
        )
        assertEquals("Ocean & Sky", metadata?.title)
        assertEquals("https://social.example.org/images/ocean.jpg", metadata?.imageUrls?.first())
    }

    @Test
    fun ignoresNonHttpsImageCandidatesAndPagesWithoutPublicImageMetadata() {
        assertNull(PublicImageMetadataParser.parse("""<meta property="og:image" content="javascript:alert(1)">""", "https://example.org/post"))
        assertNull(PublicImageMetadataParser.parse("<html><body>No image metadata</body></html>", "https://example.org"))
    }

    @Test
    fun collectsUniqueImageCandidatesInPreferenceOrder() {
        val metadata = PublicImageMetadataParser.parse(
            """
                <meta property="og:image" content="https://cdn.example.org/one.jpg">
                <meta name="twitter:image" content="https://cdn.example.org/two.png">
                <meta property="og:image" content="https://cdn.example.org/one.jpg">
            """.trimIndent(),
            "https://example.org/post"
        )
        assertEquals(
            listOf("https://cdn.example.org/one.jpg", "https://cdn.example.org/two.png"),
            metadata?.imageUrls
        )
    }
}

class ImageSignatureDetectorTest {
    @Test
    fun detectsJpegPngGifWebpAndBmpSignatures() {
        assertEquals("jpg", ImageSignatureDetector.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))?.extension)
        assertEquals(
            "png",
            ImageSignatureDetector.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))?.extension
        )
        assertEquals("gif", ImageSignatureDetector.detect("GIF89a".toByteArray())?.extension)
        val webp = ByteArray(12)
        "RIFF".toByteArray().copyInto(webp, 0)
        "WEBP".toByteArray().copyInto(webp, 8)
        assertEquals("webp", ImageSignatureDetector.detect(webp)?.extension)
        assertEquals("bmp", ImageSignatureDetector.detect("BMxxxx".toByteArray())?.extension)
    }

    @Test
    fun rejectsHtmlAndVideoSignaturesAsImages() {
        assertNull(ImageSignatureDetector.detect("<html><body>no</body></html>".toByteArray()))
        assertNull(ImageSignatureDetector.detect("....ftypisom".toByteArray()))
    }

    @Test
    fun detectsAvifAndHeifBrands() {
        val avif = ByteArray(12)
        "....ftypavif".toByteArray().copyInto(avif)
        val heif = ByteArray(12)
        "....ftypheic".toByteArray().copyInto(heif)
        assertEquals("avif", ImageSignatureDetector.detect(avif)?.extension)
        assertEquals("heic", ImageSignatureDetector.detect(heif)?.extension)
        assertTrue(ImageSignatureDetector.detect(avif) != null)
    }
}
