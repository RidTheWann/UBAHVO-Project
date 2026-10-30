package com.ridthewann.ubahvo.data

import android.content.ContextWrapper
import android.net.Uri
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLinkDownloaderHttpTest {
    @Test
    fun rejectsRedirectToPrivateIpBeforeMakingSecondRequest() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "http://127.0.0.1:" + server.port + "/private.png")
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(server, cache, publisher = publisher)
                val processId = "redirect-private"
                val error = expectDownloadFailure {
                    downloader.download(server.url("/start").toString(), processId) { _, _, _ -> }
                }

                assertTrue(error.userMessage.contains("Redirect"))
                assertEquals("Only the initial redirect response may reach the server.", 1, server.requestCount)
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsDnsRebindingToLoopbackBeforeAnyHttpRequest() {
        withServer { server ->
            val fakeDns = PublicOnlyDns(FixedDns(listOf(InetAddress.getByName("127.0.0.1"))))
            val normalizer: (String) -> String? = { raw ->
                val uri = runCatching { URI(raw) }.getOrNull()
                uri?.takeIf {
                    it.scheme == "http" &&
                        it.host == "cdn.example.org" &&
                        it.port == server.port &&
                        it.rawUserInfo == null
                }?.toASCIIString()
            }
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(
                    server = server,
                    cache = cache,
                    publisher = publisher,
                    dns = fakeDns,
                    urlNormalizer = normalizer
                )
                val processId = "dns-loopback"
                val error = expectDownloadFailure {
                    downloader.download("http://cdn.example.org:" + server.port + "/image.jpg", processId) { _, _, _ -> }
                }

                assertTrue(error.userMessage.contains("DNS") || error.cause is UnknownHostException)
                assertEquals("A non-global DNS answer must be rejected before connect.", 0, server.requestCount)
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsMixedPublicAndPrivateDnsAnswersAsAWhole() {
        withServer { server ->
            val fakeDns = PublicOnlyDns(
                FixedDns(
                    listOf(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("127.0.0.1")
                    )
                )
            )
            val normalizer: (String) -> String? = { raw ->
                val uri = runCatching { URI(raw) }.getOrNull()
                uri?.takeIf { it.host == "cdn.example.org" && it.port == server.port }?.toASCIIString()
            }
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(
                    server = server,
                    cache = cache,
                    publisher = publisher,
                    dns = fakeDns,
                    urlNormalizer = normalizer
                )
                val processId = "dns-mixed"
                expectDownloadFailure {
                    downloader.download("http://cdn.example.org:" + server.port + "/image.jpg", processId) { _, _, _ -> }
                }

                assertEquals(0, server.requestCount)
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsOversizedImageFromContentLengthBeforePublishing() {
        withServer { server ->
            val header = jpegBytes(512)
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(header))
                    .setHeader("Content-Length", (1024 * 1024 + 1).toString())
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(
                    server = server,
                    cache = cache,
                    publisher = publisher,
                    maxImageBytes = 1024L * 1024L
                )
                val processId = "large-declared"
                val error = expectDownloadFailure {
                    downloader.download(server.url("/large.jpg").toString(), processId) { _, _, _ -> }
                }

                assertEquals("Gambar melebihi batas 1 MB untuk satu file.", error.userMessage)
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun enforcesImageLimitWhenResponseUsesChunkedTransfer() {
        withServer { server ->
            val payload = jpegBytes(17)
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .chunkedBody(Buffer().write(payload), maxChunkSize = 5)
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(
                    server = server,
                    cache = cache,
                    publisher = publisher,
                    maxImageBytes = 16
                )
                val processId = "large-chunked"
                expectDownloadFailure {
                    downloader.download(server.url("/chunked.jpg").toString(), processId) { _, _, _ -> }
                }

                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsNonImagePayloadEvenWhenServerClaimsImageMimeType() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body("not a raster image")
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(server, cache, publisher = publisher)
                val processId = "fake-image"
                val error = expectDownloadFailure {
                    downloader.download(server.url("/fake.jpg").toString(), processId) { _, _, _ -> }
                }

                assertTrue(error.userMessage.contains("bukan gambar raster"))
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsHtmlPageThatExceedsMetadataReadLimit() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/html; charset=utf-8")
                    .body("<html><body>0123456789</body></html>")
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(
                    server = server,
                    cache = cache,
                    publisher = publisher,
                    maxHtmlBytes = 8
                )
                val processId = "large-html"
                val error = expectDownloadFailure {
                    downloader.download(server.url("/page").toString(), processId) { _, _, _ -> }
                }

                assertTrue(error.userMessage.contains("Halaman terlalu besar"))
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsTruncatedResponseAndRemovesPartialStagingFile() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .body(Buffer().write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
                    .setHeader("Content-Length", "100")
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(server, cache, publisher = publisher)
                val processId = "truncated"
                expectDownloadFailure {
                    downloader.download(server.url("/truncated.jpg").toString(), processId) { _, _, _ -> }
                }

                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun rejectsPrivateImageUrlFromOpenGraphMetadataWithoutFetchingIt() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/html")
                    .body(
                        "<html><head><meta property=\"og:image\" content=\"http://127.0.0.1:" +
                            server.port + "/private.jpg\"></head></html>"
                    )
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(server, cache, publisher = publisher)
                val processId = "private-og-image"
                expectDownloadFailure {
                    downloader.download(server.url("/post").toString(), processId) { _, _, _ -> }
                }

                assertEquals("Only the public page request may reach the server.", 1, server.requestCount)
                assertEquals(0, publisher.publishCalls)
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    @Test
    fun cancellationStopsTheInFlightHttpCallAndRemovesTheWorkDirectory() {
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "image/jpeg")
                    .bodyDelay(10, TimeUnit.SECONDS)
                    .body(Buffer().write(jpegBytes(3)))
                    .build()
            )
            withCacheDirectory { cache ->
                val publisher = RejectingPublisher()
                val downloader = createDownloader(server, cache, publisher = publisher)
                val processId = "cancel-http"
                runBlocking {
                    val job = async(Dispatchers.IO) {
                        downloader.download(server.url("/slow.jpg").toString(), processId) { _, _, _ -> }
                    }
                    val request = withContext(Dispatchers.IO) {
                        server.takeRequest(5, TimeUnit.SECONDS)
                    }
                    assertNotNull("The mock server must receive the in-flight request.", request)

                    job.cancel()
                    downloader.cancel(processId)
                    val outcome = runCatching { job.await() }
                    job.join()

                    assertTrue(
                        "The request coroutine must finish as cancelled, not as a download failure.",
                        outcome.exceptionOrNull() is CancellationException
                    )
                    assertEquals(0, publisher.publishCalls)
                }
                assertNoWorkDirectory(cache, processId)
            }
        }
    }

    private suspend fun expectDownloadFailure(block: suspend () -> PublishedOutput): ImageLinkException {
        return try {
            block()
            throw AssertionError("Expected the image download to fail.")
        } catch (error: ImageLinkException) {
            error
        }
    }

    private fun createDownloader(
        server: MockWebServer,
        cache: File,
        publisher: ImageOutputPublisher,
        dns: Dns = Dns.SYSTEM,
        urlNormalizer: ((String) -> String?)? = null,
        maxImageBytes: Long = 100L * 1024L * 1024L,
        maxHtmlBytes: Long = 3L * 1024L * 1024L
    ): ImageLinkDownloader {
        val context = object : ContextWrapper(null) {
            override fun getCacheDir(): File = cache
        }
        val testClient = OkHttpClient.Builder()
            .dns(dns)
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val normalizer = urlNormalizer ?: { raw ->
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
            publisher = publisher,
            httpClientOverride = testClient,
            urlNormalizer = normalizer,
            maxImageBytes = maxImageBytes,
            maxHtmlBytes = maxHtmlBytes
        )
    }

    private fun jpegBytes(size: Int): ByteArray {
        require(size >= 3)
        return ByteArray(size) { 0x41 }.apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xD8.toByte()
            this[2] = 0xFF.toByte()
        }
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }

    private fun withCacheDirectory(block: (File) -> Unit) {
        val cache = kotlin.io.path.createTempDirectory("ubahvo-image-http-test-").toFile()
        try {
            block(cache)
        } finally {
            cache.deleteRecursively()
        }
    }

    private fun assertNoWorkDirectory(cache: File, processId: String) {
        assertFalse(
            "A completed, failed, or cancelled image request must remove its private work directory.",
            File(cache, "ubahvo-image-links/$processId").exists()
        )
    }

    private class FixedDns(private val answers: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = answers
    }

    private class RejectingPublisher : ImageOutputPublisher {
        var publishCalls: Int = 0
            private set

        override fun publish(
            source: File,
            mimeType: String,
            displayNameOverride: String?,
            cancellationCheck: () -> Unit
        ): PublishedOutput {
            publishCalls++
            throw AssertionError("Invalid or cancelled HTTP responses must not be published.")
        }

        override fun discard(uri: Uri) = Unit
    }
}
