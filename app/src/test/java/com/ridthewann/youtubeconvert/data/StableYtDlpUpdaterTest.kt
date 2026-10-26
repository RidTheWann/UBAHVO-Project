package com.ridthewann.youtubeconvert.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class StableYtDlpUpdaterTest {
    @Test
    fun calculatesKnownSha256Digest() {
        val digest = StableYtDlpReleaseVerifier.sha256Hex("abc".toByteArray(Charsets.UTF_8))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            digest
        )
    }

    @Test
    fun allowsOnlyGitHubReleaseAndAssetCdnHosts() {
        assertTrue(StableYtDlpReleaseVerifier.isAllowedAssetHost("github.com"))
        assertTrue(StableYtDlpReleaseVerifier.isAllowedAssetHost("release-assets.githubusercontent.com"))
        assertFalse(StableYtDlpReleaseVerifier.isAllowedAssetHost("github.com.attacker.example"))
        assertFalse(StableYtDlpReleaseVerifier.isAllowedAssetHost("attacker.example"))
    }

    @Test
    fun parserAcceptsOfficialReleaseWithSha256Digest() {
        val payload = "#!/usr/bin/env python3\nprint('yt-dlp')\n".toByteArray(Charsets.UTF_8)
        val digest = StableYtDlpReleaseVerifier.sha256Hex(payload)
        val json = """
            {
              "tag_name": "2026.10.21",
              "name": "yt-dlp 2026.10.21",
              "assets": [{
                "name": "yt-dlp",
                "browser_download_url": "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp",
                "size": %d,
                "digest": "sha256:%s"
              }]
            }
        """.trimIndent().format(payload.size, digest)

        val metadata = StableYtDlpReleaseVerifier.parseRelease(json.toByteArray(Charsets.UTF_8))
        StableYtDlpReleaseVerifier.verifyPayload(payload, metadata)

        assertEquals("2026.10.21", metadata.tag)
        assertEquals("yt-dlp 2026.10.21", metadata.name)
        assertEquals(payload.size.toLong(), metadata.sizeBytes)
        assertEquals(digest, metadata.sha256)
    }

    @Test
    fun parserRejectsUntrustedAssetHost() {
        val json = """
            {
              "tag_name": "2026.10.21",
              "name": "yt-dlp 2026.10.21",
              "assets": [{
                "name": "yt-dlp",
                "browser_download_url": "https://attacker.example/yt-dlp",
                "size": 10,
                "digest": "sha256:%s"
              }]
            }
        """.trimIndent().format("a".repeat(64))

        try {
            StableYtDlpReleaseVerifier.parseRelease(json.toByteArray(Charsets.UTF_8))
            fail("Expected an IOException for an untrusted asset host.")
        } catch (_: IOException) {
            // Expected: updater fails closed on an arbitrary asset host.
        }
    }

    @Test
    fun integrityVerificationRejectsTamperedPayload() {
        val expectedPayload = "#!/usr/bin/env python3\nprint('trusted')\n".toByteArray(Charsets.UTF_8)
        val tamperedPayload = "#!/usr/bin/env python3\nprint('changed')\n".toByteArray(Charsets.UTF_8)
        val metadata = StableYtDlpRelease(
            tag = "2026.10.21",
            name = "yt-dlp 2026.10.21",
            assetUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp",
            sizeBytes = expectedPayload.size.toLong(),
            sha256 = StableYtDlpReleaseVerifier.sha256Hex(expectedPayload)
        )

        try {
            StableYtDlpReleaseVerifier.verifyPayload(tamperedPayload, metadata)
            fail("Expected an IOException when payload checksum differs.")
        } catch (_: IOException) {
            // Expected: modified bytes must never be installed.
        }
    }
}
