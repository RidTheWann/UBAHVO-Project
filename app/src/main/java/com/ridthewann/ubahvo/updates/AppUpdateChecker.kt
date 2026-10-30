package com.ridthewann.ubahvo.updates

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class AppUpdateInfo(
    val tag: String,
    val versionName: String,
    val releaseUrl: String
)

internal object AppUpdateChecker {
    const val PREFERENCES_NAME = "ubahvo_app_updates"
    const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    private const val KEY_LAST_CHECK_AT = "last_check_at_ms"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val KEY_PENDING_TAG = "pending_tag"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_URL = "pending_url"
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    private const val API_URL = "https://api.github.com/repos/RidTheWann/UBAHVO-Project/releases?per_page=20"
    private const val MAX_RESPONSE_BYTES = 1 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val USER_AGENT = "UBAHVO-Android"

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val beta: Int?
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)
            if (beta == null && other.beta != null) return 1
            if (beta != null && other.beta == null) return -1
            return if (beta != null && other.beta != null) beta.compareTo(other.beta) else 0
        }
    }

    fun checkForUpdate(context: Context): AppUpdateInfo? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val currentVersion = installedVersion(context)
        val pending = pendingUpdate(
            preferences.getString(KEY_PENDING_TAG, null),
            preferences
        )
        if (pending != null &&
            isNewerVersion(pending.versionName, currentVersion) &&
            preferences.getString(KEY_LAST_NOTIFIED_TAG, null) != pending.tag
        ) {
            return pending
        }
        if (pending != null && !isNewerVersion(pending.versionName, currentVersion)) {
            preferences.edit()
                .remove(KEY_PENDING_TAG)
                .remove(KEY_PENDING_VERSION)
                .remove(KEY_PENDING_URL)
                .apply()
        }

        val now = System.currentTimeMillis()
        if (now - preferences.getLong(KEY_LAST_CHECK_AT, 0L) < CHECK_INTERVAL_MS) return null
        try {
            val candidate = findNewerRelease(fetchReleases(), currentVersion)
            preferences.edit().putLong(KEY_LAST_CHECK_AT, now).apply()
            if (candidate == null ||
                preferences.getString(KEY_LAST_NOTIFIED_TAG, null) == candidate.tag
            ) {
                preferences.edit()
                    .remove(KEY_PENDING_TAG)
                    .remove(KEY_PENDING_VERSION)
                    .remove(KEY_PENDING_URL)
                    .apply()
                return null
            }

            preferences.edit()
                .putString(KEY_PENDING_TAG, candidate.tag)
                .putString(KEY_PENDING_VERSION, candidate.versionName)
                .putString(KEY_PENDING_URL, candidate.releaseUrl)
                .apply()
            return candidate
        } catch (error: Exception) {
            Log.w("UBAHVO", "Could not check for an app update; it will retry on a later launch/job", error)
            throw error
        }
    }

    fun markNotified(context: Context, tag: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFIED_TAG, tag)
            .remove(KEY_PENDING_TAG)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_URL)
            .apply()
    }

    internal fun findNewerRelease(releasesJson: String, currentVersion: String): AppUpdateInfo? {
        val current = parseVersion(currentVersion) ?: return null
        val releases = try {
            JSON_MAPPER.readTree(releasesJson)
        } catch (error: Exception) {
            throw IOException("The UBAHVO release list is invalid.", error)
        }
        if (releases == null || !releases.isArray) {
            throw IOException("The UBAHVO release list must be a JSON array.")
        }

        var newest: Pair<Version, AppUpdateInfo>? = null
        for (release in releases) {
            if (!release.isObject || release.path("draft").asBoolean(false)) continue
            val tag = release.path("tag_name")
                .takeIf { it.isTextual }?.asText().orEmpty()
            val version = parseVersion(tag) ?: continue
            val rawReleaseUrl = release.path("html_url")
                .takeIf { it.isTextual }?.asText().orEmpty()
            val releaseUrl = validateReleaseUrl(rawReleaseUrl, tag) ?: continue
            val assets = release.path("assets")
            if (!assets.isArray) continue

            val assetNamesAndSizes = assets.mapNotNull { asset ->
                if (!asset.isObject) return@mapNotNull null
                val nameNode = asset.path("name")
                val sizeNode = asset.path("size")
                if (!nameNode.isTextual) return@mapNotNull null
                val size = if (sizeNode.isIntegralNumber && sizeNode.canConvertToLong()) {
                    sizeNode.asLong()
                } else 0L
                nameNode.asText() to size
            }
            if (!hasSupportedApkAsset(assetNamesAndSizes, tag) || version <= current) continue

            val info = AppUpdateInfo(tag, tag.removePrefix("v"), releaseUrl)
            if (newest == null || version > newest.first) newest = version to info
        }
        return newest?.second
    }


    /**
     * Accept the versioned beta debug APK and the versioned production APK naming convention.
     * A tag-specific exact name prevents an unrelated/stale ARM64 APK from advertising an update.
     */
    internal fun hasSupportedApkAsset(
        assetNamesAndSizes: Iterable<Pair<String, Long>>,
        tag: String
    ): Boolean {
        val version = tag.removePrefix("v")
        val acceptedNames = setOf(
            "UBAHVO-$version-universal-debug.apk",
            "UBAHVO-$version-universal.apk",
            "UBAHVO-$version-arm64-v8a-debug.apk",
            "UBAHVO-$version-arm64-v8a.apk"
        )
        return assetNamesAndSizes.any { (name, size) ->
            size > 0L && acceptedNames.any { it.equals(name, ignoreCase = true) }
        }
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val newVersion = parseVersion(candidate) ?: return false
        val currentVersion = parseVersion(current) ?: return false
        return newVersion > currentVersion
    }

    private fun pendingUpdate(
        tag: String?,
        preferences: android.content.SharedPreferences
    ): AppUpdateInfo? {
        if (tag.isNullOrBlank()) return null
        val url = preferences.getString(KEY_PENDING_URL, null) ?: return null
        val version = preferences.getString(KEY_PENDING_VERSION, null) ?: return null
        return AppUpdateInfo(tag, version, url)
    }

    private fun installedVersion(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    private fun parseVersion(input: String): Version? {
        val match = VERSION_PATTERN.matchEntire(input.trim()) ?: return null
        return try {
            Version(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                beta = match.groupValues[4].takeIf { it.isNotBlank() }?.toInt()
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun validateReleaseUrl(rawUrl: String, tag: String): String? {
        return try {
            val uri = URI(rawUrl)
            val expectedPath = "/RidTheWann/UBAHVO-Project/releases/tag/$tag"
            if (uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("github.com", ignoreCase = true) &&
                uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.path == expectedPath
            ) uri.toASCIIString() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchReleases(): String {
        val uri = URI(API_URL)
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals("api.github.com", ignoreCase = true)
        ) throw IOException("App-update checks require the official GitHub HTTPS API.")

        val connection = URL(API_URL).openConnection() as? HttpsURLConnection
            ?: throw IOException("App-update checks require HTTPS.")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

            val status = connection.responseCode
            if (status != HttpsURLConnection.HTTP_OK) {
                throw IOException("The app-update server returned HTTP $status.")
            }

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) {
                        throw IOException("The app-update response is too large.")
                    }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private val JSON_MAPPER = ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    private val VERSION_PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?""")
}
