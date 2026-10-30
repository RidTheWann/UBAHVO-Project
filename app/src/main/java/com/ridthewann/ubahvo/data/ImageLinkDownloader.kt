package com.ridthewann.ubahvo.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class ImageLinkException(val userMessage: String, cause: Throwable? = null) : IOException(userMessage, cause)

/**
 * First-party public image fetcher: a direct raster-image URL or an image declared by public
 * Open Graph/Twitter metadata. It does not execute JavaScript or bypass source-service controls.
 *
 * OkHttp's custom Dns receives the validated InetAddress list and uses that exact list for route
 * selection. The original URL hostname remains intact for SNI and TLS certificate verification.
 */
internal class ImageLinkDownloader(
    private val context: Context,
    private val publisher: OutputPublisher
) {
    private data class OpenedResponse(
        val finalUrl: String,
        val response: Response,
        val call: Call
    )
    private data class StagedImage(val file: File, val format: RecognizedImageFormat, val displayName: String)

    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns())
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun download(
        url: String,
        processId: String,
        onProgress: (progress: Float, etaSeconds: Long, outputLine: String) -> Unit
    ): PublishedOutput = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        val workDirectory = File(File(context.cacheDir, WORK_DIRECTORY), processId)
        if (!workDirectory.mkdirs() && !workDirectory.isDirectory) {
            throw ImageLinkException("Ruang kerja gambar tidak dapat disiapkan.")
        }
        try {
            onProgress(0f, 0L, "IMAGE_STAGE:Memeriksa tautan gambar publik…")
            val page = openFollowingRedirects(processId, url, IMAGE_ACCEPT)
            val pageBody = page.response.body
            val pageInput = BufferedInputStream(pageBody.byteStream())
            val pagePrefix = ImagePrefixReader.readPrefix(pageInput, HEADER_BYTES)
            val pageType = mediaType(page.response.header("Content-Type"))
            val htmlPage = pageType == "text/html" || pageType == "application/xhtml+xml" || looksLikeHtml(pagePrefix)
            val staged = if (!htmlPage) {
                stageImage(pageInput, page, pagePrefix, workDirectory, null, coroutineContext, onProgress)
            } else {
                onProgress(1f, 0L, "IMAGE_STAGE:Mencari gambar utama dari metadata halaman…")
                val html = readLimitedHtml(pageInput, pageBody.contentLength(), coroutineContext)
                closeResponse(processId, page)
                val metadata = PublicImageMetadataParser.parse(html, page.finalUrl)
                    ?: throw ImageLinkException(
                        "Halaman ini tidak menyediakan gambar publik melalui metadata standar. " +
                            "Beberapa posting carousel memerlukan dukungan khusus dan belum bisa diekstrak."
                    )
                metadata.title?.let { onProgress(2f, 0L, "IMAGE_TITLE:$it") }
                val candidates = metadata.imageUrls.mapNotNull { MediaUrlValidator.normalize(it) }
                    .distinct().take(MAX_IMAGE_CANDIDATES)
                if (candidates.isEmpty()) {
                    throw ImageLinkException("Halaman tidak memberikan URL gambar HTTPS yang aman.")
                }

                var chosen: StagedImage? = null
                var lastError: Exception? = null
                for (candidate in candidates) {
                    coroutineContext.ensureActive()
                    try {
                        val imageResponse = openFollowingRedirects(processId, candidate, IMAGE_ACCEPT)
                        val imageBody = imageResponse.response.body
                        val imageInput = BufferedInputStream(imageBody.byteStream())
                        val imagePrefix = ImagePrefixReader.readPrefix(imageInput, HEADER_BYTES)
                        val imageType = mediaType(imageResponse.response.header("Content-Type"))
                        if (imageType == "text/html" || imageType == "application/xhtml+xml" || looksLikeHtml(imagePrefix)) {
                            imageInput.close()
                            closeResponse(processId, imageResponse)
                            continue
                        }
                        chosen = stageImage(
                            imageInput, imageResponse, imagePrefix, workDirectory, metadata.title,
                            coroutineContext, onProgress
                        )
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        lastError = error
                    }
                }
                chosen ?: throw ImageLinkException(
                    "Metadata ditemukan, tetapi gambar tidak dapat diunduh. Coba tautan gambar langsung jika tersedia.",
                    lastError
                )
            }

            coroutineContext.ensureActive()
            onProgress(96f, 0L, "IMAGE_STAGE:Menyimpan gambar ke Download/UBAHVO…")
            val published = publisher.publish(staged.file, staged.format.mimeType, staged.displayName) {
                coroutineContext.ensureActive()
            }
            try {
                // Close the final cancellation race: the MediaStore row may have become visible
                // just before this coroutine receives cancellation.
                coroutineContext.ensureActive()
            } catch (cancelled: CancellationException) {
                publisher.discard(published.uri)
                throw cancelled
            }
            onProgress(100f, 0L, "IMAGE_STAGE:Gambar siap dibuka atau dibagikan.")
            published
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ImageLinkException) {
            throw error
        } catch (error: Exception) {
            throw ImageLinkException(
                "Gambar belum berhasil disimpan. Pastikan tautan publik aktif, koneksi stabil, dan penyimpanan cukup.",
                error
            )
        } finally {
            activeCalls.remove(processId)?.cancel()
            workDirectory.deleteRecursively()
        }
    }

    fun cancel(processId: String) {
        activeCalls.remove(processId)?.cancel()
    }

    private suspend fun openFollowingRedirects(
        processId: String,
        initialUrl: String,
        accept: String
    ): OpenedResponse {
        var currentUrl = validatePublicUrl(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            currentCoroutineContext().ensureActive()
            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Encoding", "identity")
                .build()
            val call = httpClient.newCall(request)
            activeCalls[processId] = call
            currentCoroutineContext().ensureActive()

            val response = try {
                call.execute()
            } catch (cancelled: CancellationException) {
                call.cancel()
                activeCalls.remove(processId, call)
                throw cancelled
            } catch (error: Exception) {
                call.cancel()
                activeCalls.remove(processId, call)
                if (error is UnknownHostException) {
                    throw ImageLinkException(
                        "Host gambar tidak dapat dijangkau atau DNS tidak menunjuk ke alamat publik.",
                        error
                    )
                }
                throw error
            }

            val opened = OpenedResponse(currentUrl, response, call)
            val status = response.code
            if (status in REDIRECT_CODES) {
                val location = response.header("Location")
                closeResponse(processId, opened)
                if (location.isNullOrBlank() || redirectCount >= MAX_REDIRECTS) {
                    throw ImageLinkException("Tautan gambar terlalu banyak dialihkan atau redirect-nya tidak valid.")
                }
                currentUrl = try {
                    validatePublicUrl(URI(currentUrl).resolve(location).toString())
                } catch (error: Exception) {
                    throw ImageLinkException("Redirect menuju tautan yang tidak aman atau tidak didukung.", error)
                }
                return@repeat
            }
            if (status !in 200..299) {
                closeResponse(processId, opened)
                throw ImageLinkException("Sumber menolak permintaan gambar (HTTP $status).")
            }
            return opened
        }
        throw ImageLinkException("Tautan gambar dialihkan terlalu banyak kali.")
    }

    private suspend fun stageImage(
        input: BufferedInputStream,
        response: OpenedResponse,
        prefix: ByteArray,
        workDirectory: File,
        fallbackTitle: String?,
        coroutineContext: kotlin.coroutines.CoroutineContext,
        onProgress: (Float, Long, String) -> Unit
    ): StagedImage {
        try {
            input.use { stream ->
                val format = ImageSignatureDetector.detect(prefix)
                    ?: throw ImageLinkException(
                        "Tautan ini bukan gambar raster yang didukung. SVG dan file non-gambar tidak disimpan sebagai wallpaper."
                    )
                val expectedBytes = response.response.body.contentLength()
                if (expectedBytes > MAX_IMAGE_BYTES) {
                    throw ImageLinkException("Gambar melebihi batas " + (MAX_IMAGE_BYTES / (1024 * 1024)) + " MB untuk satu file.")
                }
                val available = workDirectory.usableSpace
                if (available <= MINIMUM_FREE_SPACE_BYTES ||
                    expectedBytes > 0L && expectedBytes > available - MINIMUM_FREE_SPACE_BYTES
                ) {
                    throw ImageLinkException("Ruang sementara tidak cukup untuk menyimpan gambar ini.")
                }
                val file = File(workDirectory, "image." + format.extension)
                var totalBytes = 0L
                var lastReportedPercent = -1f
                try {
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_IMAGE_BYTES ||
                                totalBytes > (available - MINIMUM_FREE_SPACE_BYTES).coerceAtLeast(0L)
                            ) {
                                throw ImageLinkException("Gambar terlalu besar atau ruang sementara tidak mencukupi.")
                            }
                            output.write(buffer, 0, count)
                            val percent = if (expectedBytes > 0L) {
                                (5f + 90f * (totalBytes.toDouble() / expectedBytes).coerceIn(0.0, 1.0)).toFloat()
                            } else 5f
                            if (percent - lastReportedPercent >= 1f) {
                                lastReportedPercent = percent
                                onProgress(
                                    percent, 0L,
                                    if (expectedBytes > 0L) "IMAGE_MODE:determinate" else "IMAGE_MODE:indeterminate"
                                )
                            }
                        }
                    }
                    if (totalBytes <= 0L) throw ImageLinkException("Sumber menghasilkan file gambar kosong.")
                    if (expectedBytes > 0L && totalBytes != expectedBytes) {
                        throw ImageLinkException("Unduhan gambar tidak lengkap; ukuran hasil berbeda dari yang dilaporkan sumber.")
                    }
                    onProgress(95f, 0L, "IMAGE_MODE:determinate")
                    val base = safeBaseName(fallbackTitle ?: filenameFromUrl(response.finalUrl))
                    return StagedImage(file, format, base + "." + format.extension)
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
            }
        } finally {
            closeResponse(response)
        }
    }

    private suspend fun readLimitedHtml(
        input: BufferedInputStream,
        expectedBytes: Long,
        coroutineContext: kotlin.coroutines.CoroutineContext
    ): String {
        input.use { stream ->
            if (expectedBytes > MAX_HTML_BYTES) throw ImageLinkException("Halaman terlalu besar untuk dianalisis dengan aman.")
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_HTML_BYTES) {
                    throw ImageLinkException("Halaman terlalu besar untuk dianalisis dengan aman.")
                }
                output.write(buffer, 0, count)
            }
            return decodeHtml(output.toByteArray())
        }
    }

    private fun looksLikeHtml(prefix: ByteArray): Boolean {
        val text = String(prefix, StandardCharsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            .lowercase(Locale.ROOT)
        return text.startsWith("<!doctype html") || text.startsWith("<html") ||
            text.startsWith("<head") || text.startsWith("<meta") || text.startsWith("<!doctype")
    }

    private fun decodeHtml(bytes: ByteArray): String {
        val declaration = String(bytes, 0, minOf(bytes.size, 512), StandardCharsets.US_ASCII)
        val charsetName = Regex("""charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(declaration)?.groupValues?.getOrNull(1)
        val charset = runCatching { Charset.forName(charsetName ?: "UTF-8") }.getOrDefault(StandardCharsets.UTF_8)
        return String(bytes, charset)
    }

    private fun validatePublicUrl(input: String): String =
        MediaUrlValidator.normalize(input)
            ?: throw ImageLinkException("Gunakan tautan HTTPS publik yang valid.")

    private fun closeResponse(processId: String, opened: OpenedResponse) {
        activeCalls.remove(processId, opened.call)
        closeResponse(opened)
    }

    private fun closeResponse(opened: OpenedResponse) {
        opened.response.close()
        opened.call.cancel()
    }

    private fun mediaType(value: String?): String = value.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)

    private fun filenameFromUrl(url: String): String {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val lastSegment = path.substringAfterLast('/')
        return lastSegment.substringBeforeLast('.', lastSegment).takeIf { it.isNotBlank() } ?: "wallpaper"
    }

    private fun safeBaseName(value: String): String = value
        .map { char -> if (char.isLetterOrDigit() || char in " ._()-[]") char else '_' }
        .joinToString("").trim().take(96).ifBlank { "wallpaper" }

    companion object {
        private const val WORK_DIRECTORY = "ubahvo-image-links"
        private const val USER_AGENT = "UBAHVO/0.1 (Android; public image fetch)"
        private const val IMAGE_ACCEPT = "image/avif,image/webp,image/png,image/jpeg,image/gif,image/*,text/html;q=0.8,*/*;q=0.5"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_IMAGE_CANDIDATES = 5
        private const val MAX_IMAGE_BYTES = 100L * 1024L * 1024L
        private const val MAX_HTML_BYTES = 3L * 1024L * 1024L
        private const val MINIMUM_FREE_SPACE_BYTES = 8L * 1024L * 1024L
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val HEADER_BYTES = 512
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
