package com.ridthewann.ubahvo.data

import android.content.ContextWrapper
import java.io.File
import java.net.URI
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLinkDownloaderHttpTest {
    @Test
    fun rejectsRedirectToPrivateIpBeforeMakingSecondRequest() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "http://127.0.0.1:${server.port}/private.png")
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache)
                assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/start").toString(), "redirect-private") { _, _, _ -> }
                    }
                }
                assertEquals("Only the initial redirect response may reach the server.", 1, server.requestCount)
                assertNoStagedFiles(cache)
            }
        }
    }

    @Test
    fun rejectsOversizedImageFromContentLengthBeforePublishing() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(ByteArray(17) { 0x41 }))
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache, maxImageBytes = 16)
                val error = assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/large.jpg").toString(), "large-declared") { _, _, _ -> }
                    }
                }
                assertEquals("Gambar melebihi batas 0 MB untuk satu file.", error.userMessage)
                assertNoStagedFiles(cache)
            }
        }
    }

    @Test
    fun enforcesImageLimitWhenResponseUsesChunkedTransfer() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .chunkedBody(Buffer().write(ByteArray(17) { 0x42 }), maxChunkSize = 5)
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache, maxImageBytes = 16)
                assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/chunked.jpg").toString(), "large-chunked") { _, _, _ -> }
                    }
                }
                assertNoStagedFiles(cache)
            }
        }
    }

    @Test
    fun rejectsNonImagePayloadEvenWhenServerClaimsImageMimeType() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body("not a raster image")
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache)
                val error = assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/fake.jpg").toString(), "fake-image") { _, _, _ -> }
                    }
                }
                assertEquals(
                    "Tautan ini bukan gambar raster yang didukung. SVG dan file non-gambar tidak disimpan sebagai wallpaper.",
                    error.userMessage
                )
                assertNoStagedFiles(cache)
            }
        }
    }

    @Test
    fun rejectsHtmlPageThatExceedsMetadataReadLimit() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/html; charset=utf-8")
                    .body("<html><body>0123456789</body></html>")
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache, maxHtmlBytes = 8)
                assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/page").toString(), "large-html") { _, _, _ -> }
                    }
                }
                assertNoStagedFiles(cache)
            }
        }
    }

    @Test
    fun rejectsTruncatedResponseAndRemovesPartialStagingFile() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
                    .setHeader("Content-Length", 100)
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache)
                assertThrows(ImageLinkException::class.java) {
                    runBlocking {
                        downloader.download(server.url("/truncated.jpg").toString(), "truncated") { _, _, _ -> }
                    }
                }
                assertNoStagedFiles(cache)
            }
        }
    }


    @Test
    fun cancellationStopsTheInFlightHttpCallAndRemovesTheWorkDirectory() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .bodyDelay(10, TimeUnit.SECONDS)
                    .body(Buffer().write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
                    .build()
            )
            withCacheDirectory { cache ->
                val downloader = createDownloader(server, cache)
                runBlocking {
                    val job = launch {
                        downloader.download(server.url("/slow.jpg").toString(), "cancel-http") { _, _, _ -> }
                    }
                    val requestDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (server.requestCount == 0 && System.nanoTime() < requestDeadline) {
                        Thread.sleep(10)
                    }
                    assertEquals("The mock server should receive the request.", 1, server.requestCount)
                    job.cancel()
                    downloader.cancel("cancel-http")
                    job.join()
                    assertTrue("The coroutine should finish as cancelled.", job.isCancelled)
                }
                assertNoStagedFiles(cache)
            }
        }
    }

    private fun createDownloader(
        server: MockWebServer,
        cache: File,
        maxImageBytes: Long = 100L * 1024L * 1024L,
        maxHtmlBytes: Long = 3L * 1024L * 1024L
    ): ImageLinkDownloader {
        val context = object : ContextWrapper(null) {
            override fun getCacheDir(): File = cache
        }
        val testClient = OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val localServerOnly: (String) -> String? = { raw ->
            val uri = runCatching { URI(raw) }.getOrNull()
            uri?.takeIf {
                it.scheme.equals("http", ignoreCase = true) &&
                    it.host.equals("localhost", ignoreCase = true) &&
                    it.port == server.port &&
                    it.rawUserInfo == null
            }?.toASCIIString()
        }
        return ImageLinkDownloader(
            context = context,
            publisher = OutputPublisher(context),
            httpClientOverride = testClient,
            urlNormalizer = localServerOnly,
            maxImageBytes = maxImageBytes,
            maxHtmlBytes = maxHtmlBytes
        )
    }

    private fun withCacheDirectory(block: (File) -> Unit) {
        val cache = kotlin.io.path.createTempDirectory("ubahvo-image-http-test-").toFile()
        try {
            block(cache)
        } finally {
            cache.deleteRecursively()
        }
    }

    private fun assertNoStagedFiles(cache: File) {
        val workDirectory = File(cache, "ubahvo-image-links")
        assertFalse("A failed image request must not leave staged files.", workDirectory.walkTopDown().any(File::isFile))
    }
}
