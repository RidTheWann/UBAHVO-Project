package com.ridthewann.youtubeconvert.data

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.yausername.youtubedl_android.YoutubeDL
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

internal data class StableYtDlpRelease(
    val tag: String,
    val name: String,
    val assetUrl: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Pure metadata and payload verification. It refuses missing digests and untrusted asset URLs.
 */
internal object StableYtDlpReleaseVerifier {
    fun parseRelease(jsonBytes: ByteArray): StableYtDlpRelease {
        val root = try {
            YoutubeDL.objectMapper.readTree(jsonBytes)
        } catch (error: Exception) {
            throw IOException("GitHub returned invalid release metadata.", error)
        }

        val tag = root.path("tag_name").asText("")
        if (!SAFE_TAG.matches(tag)) {
            throw IOException("The official yt-dlp release has an invalid version tag.")
        }
        val name = root.path("name").asText(tag).ifBlank { tag }
        val assets = root.path("assets")
        if (!assets.isArray) throw IOException("The official yt-dlp release has no asset list.")

        var ytDlpAsset: JsonNode? = null
        for (asset in assets) {
            if (asset.path("name").asText() == ASSET_NAME) {
                ytDlpAsset = asset
                break
            }
        }
        val asset = ytDlpAsset ?: throw IOException("The official release does not contain the yt-dlp asset.")

        val url = asset.path("browser_download_url").asText("")
        val uri = try {
            URI(url)
        } catch (error: Exception) {
            throw IOException("The official yt-dlp asset URL is invalid.", error)
        }
        val expectedPath = "/yt-dlp/yt-dlp/releases/download/$tag/$ASSET_NAME"
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("github.com", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443) ||
            uri.path != expectedPath
        ) {
            throw IOException("The release asset URL is not an approved official GitHub URL.")
        }

        val digestValue = asset.path("digest").asText("")
        val digest = digestValue.removePrefix("sha256:").lowercase(Locale.ROOT)
        if (!SHA256_PATTERN.matches(digest)) {
            throw IOException("GitHub did not provide a valid SHA-256 digest for yt-dlp; update refused.")
        }

        val size = asset.path("size").asLong(-1L)
        if (size <= 0L || size > MAX_ASSET_BYTES.toLong()) {
            throw IOException("The published yt-dlp asset has an unexpected size.")
        }

        return StableYtDlpRelease(
            tag = tag,
            name = name,
            assetUrl = uri.toASCIIString(),
            sizeBytes = size,
            sha256 = digest
        )
    }

    fun verifyPayload(payload: ByteArray, release: StableYtDlpRelease) {
        if (payload.isEmpty() || payload.size.toLong() != release.sizeBytes) {
            throw IOException("The downloaded yt-dlp file size does not match the official release metadata.")
        }

        val firstLine = payload.takeWhile { it != '\n'.code.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
            .removeSuffix("\r")
        if (!firstLine.startsWith("#!")) {
            throw IOException("The downloaded yt-dlp file does not look like the expected script.")
        }

        val actualDigest = sha256Hex(payload)
        if (!MessageDigest.isEqual(
                actualDigest.toByteArray(Charsets.US_ASCII),
                release.sha256.toByteArray(Charsets.US_ASCII)
            )
        ) {
            throw IOException("The downloaded yt-dlp SHA-256 does not match the official release digest.")
        }
    }

    /**
     * Verify the bytes that are actually persisted on disk, not only the in-memory download.
     */
    internal fun verifyFilePayload(file: File, release: StableYtDlpRelease) {
        if (!file.isFile || file.length() != release.sizeBytes) {
            throw IOException("The on-disk yt-dlp file size does not match the official release metadata.")
        }
        verifyPayload(file.readBytes(), release)
    }

    internal fun sha256Hex(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte) }

    internal fun isAllowedAssetHost(host: String): Boolean =
        host.equals("github.com", ignoreCase = true) ||
            host.equals("release-assets.githubusercontent.com", ignoreCase = true) ||
            host.equals("objects.githubusercontent.com", ignoreCase = true)

    private const val ASSET_NAME = "yt-dlp"
    private const val MAX_ASSET_BYTES = 20 * 1024 * 1024
    private val SAFE_TAG = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    private val SHA256_PATTERN = Regex("[a-f0-9]{64}")
}

/**
 * Downloads only the official stable release, verifies its published SHA-256 digest, then
 * atomically replaces the bundled script. The previous script remains untouched on download
 * or verification failure.
 */
internal class StableYtDlpUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun update(): YtDlpUpdateResult {
        val releaseBytes = readHttpsBytes(
            url = RELEASE_API_URL,
            maxBytes = MAX_RELEASE_JSON_BYTES,
            isAllowedHost = { it == "api.github.com" },
            accept = "application/vnd.github+json"
        )
        val release = StableYtDlpReleaseVerifier.parseRelease(releaseBytes)
        if (preferences.getString(PREFERENCES_VERSION, null) == release.tag &&
            hasVerifiedInstalledPayload(release)
        ) {
            return YtDlpUpdateResult(updated = false, version = release.tag, versionName = release.name)
        }

        val payload = readHttpsBytes(
            url = release.assetUrl,
            maxBytes = MAX_ASSET_BYTES,
            isAllowedHost = StableYtDlpReleaseVerifier::isAllowedAssetHost,
            accept = "application/octet-stream"
        )
        StableYtDlpReleaseVerifier.verifyPayload(payload, release)
        installAtomically(payload, release)
        preferences.edit().putString(PREFERENCES_VERSION, release.tag).apply()

        return YtDlpUpdateResult(updated = true, version = release.tag, versionName = release.name)
    }

    /**
     * Preferences alone are not proof that the installed updater payload still exists or is valid.
     * If it was removed or corrupted, fetch and verify the release again instead of reporting success.
     */
    private fun hasVerifiedInstalledPayload(release: StableYtDlpRelease): Boolean {
        val installed = File(appContext.noBackupFilesDir, "youtubedl-android/yt-dlp/$ASSET_NAME")
        if (!installed.isFile || installed.length() != release.sizeBytes) return false

        return try {
            StableYtDlpReleaseVerifier.verifyFilePayload(installed, release)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun installAtomically(payload: ByteArray, release: StableYtDlpRelease) {
        val directory = File(appContext.noBackupFilesDir, "youtubedl-android/yt-dlp")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the private yt-dlp directory.")
        }
        if (!directory.isDirectory) throw IOException("The private yt-dlp path is not a directory.")

        val target = File(directory, ASSET_NAME)
        val temporary = File(directory, "$ASSET_NAME.pending")
        val backup = File(directory, "$ASSET_NAME.previous")
        var previousFileBackedUp = false
        var installAttempted = false

        try {
            // Recover an interrupted install, or remove a leftover backup from a completed install.
            if (backup.exists()) {
                if (target.exists()) {
                    if (!backup.delete()) {
                        throw IOException("Could not remove a stale yt-dlp backup.")
                    }
                } else {
                    moveReplacing(backup, target)
                }
            }

            FileOutputStream(temporary).use { output ->
                output.write(payload)
                output.fd.sync()
            }

            // Verify the persisted staging file before it can replace the installed downloader.
            StableYtDlpReleaseVerifier.verifyFilePayload(temporary, release)

            if (target.exists()) {
                if (!target.isFile) {
                    throw IOException("The installed yt-dlp path is not a regular file.")
                }
                previousFileBackedUp = true
                moveReplacing(target, backup)
            }

            installAttempted = true
            moveReplacing(temporary, target)

            // Verify the bytes at their final path before accepting this update.
            StableYtDlpReleaseVerifier.verifyFilePayload(target, release)

            // A leftover backup is harmless; failure to delete it must not invalidate a verified install.
            if (backup.exists()) backup.delete()
        } catch (error: Exception) {
            runCatching { temporary.delete() }

            var restoreFailure: Exception? = null
            try {
                if (previousFileBackedUp && backup.exists()) {
                    if (target.exists() && !target.delete()) {
                        throw IOException("Could not remove the failed yt-dlp installation before rollback.")
                    }
                    moveReplacing(backup, target)
                } else if (installAttempted && !previousFileBackedUp &&
                    target.exists() && !target.delete()
                ) {
                    throw IOException("Could not remove the failed yt-dlp installation.")
                }
            } catch (restoreError: Exception) {
                restoreFailure = restoreError
            }

            if (error is IOException) {
                restoreFailure?.let(error::addSuppressed)
                throw error
            }

            val wrapped = IOException("Could not install the verified yt-dlp update.", error)
            restoreFailure?.let(wrapped::addSuppressed)
            throw wrapped
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            java.nio.file.Files.move(
                source.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                source.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun readHttpsBytes(
        url: String,
        maxBytes: Int,
        isAllowedHost: (String) -> Boolean,
        accept: String
    ): ByteArray {
        var currentUrl = try {
            URL(url)
        } catch (error: Exception) {
            throw IOException("Invalid update URL.", error)
        }
        var redirectCount = 0

        while (true) {
            val currentUri = try {
                currentUrl.toURI()
            } catch (error: Exception) {
                throw IOException("Invalid redirect URL.", error)
            }
            val host = currentUri.host?.lowercase(Locale.ROOT).orEmpty()
            if (!currentUri.scheme.equals("https", ignoreCase = true) ||
                currentUri.rawUserInfo != null ||
                (currentUri.port != -1 && currentUri.port != 443) ||
                !isAllowedHost(host)
            ) {
                throw IOException("yt-dlp update attempted to use an unapproved network host.")
            }

            val connection = currentUrl.openConnection() as? javax.net.ssl.HttpsURLConnection
                ?: throw IOException("The yt-dlp updater requires HTTPS.")
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)

                val status = connection.responseCode
                if (status in 300..399) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw IOException("The yt-dlp update server redirected too many times.")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("The yt-dlp update server returned an invalid redirect.")
                    currentUrl = URL(currentUrl, location)
                    redirectCount++
                    continue
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw IOException("The yt-dlp update server returned HTTP $status.")
                }

                val contentLength = connection.contentLengthLong
                if (contentLength > maxBytes.toLong()) {
                    throw IOException("The yt-dlp update response exceeded the allowed size.")
                }

                val initialCapacity = if (contentLength > 0L) contentLength.toInt() else 0
                val out = ByteArrayOutputStream(minOf(maxBytes, initialCapacity))
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > maxBytes) {
                            throw IOException("The yt-dlp update response exceeded the allowed size.")
                        }
                        out.write(buffer, 0, count)
                    }
                }
                return out.toByteArray()
            } catch (error: IOException) {
                throw error
            } catch (error: Exception) {
                throw IOException("Failed to download the official yt-dlp release.", error)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val RELEASE_API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val ASSET_NAME = "yt-dlp"
        private const val PREFERENCES_NAME = "stable_ytdlp_updates"
        private const val PREFERENCES_VERSION = "installed_stable_tag"
        private const val USER_AGENT = "Youtube-Convert-Android"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_RELEASE_JSON_BYTES = 1 * 1024 * 1024
        private const val MAX_ASSET_BYTES = 20 * 1024 * 1024
    }
}
