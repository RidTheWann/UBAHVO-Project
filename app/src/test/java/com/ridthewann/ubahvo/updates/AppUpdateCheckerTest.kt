package com.ridthewann.ubahvo.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun newerBetaIsRecognized() {
        assertTrue(AppUpdateChecker.isNewerVersion("v0.1.0-beta.17", "0.1.0-beta.16"))
    }

    @Test
    fun olderOrSameBetaIsNotAnUpdate() {
        assertFalse(AppUpdateChecker.isNewerVersion("v0.1.0-beta.14", "0.1.0-beta.15"))
        assertFalse(AppUpdateChecker.isNewerVersion("v0.1.0-beta.16", "0.1.0-beta.16"))
    }

    @Test
    fun stableReleaseRanksAboveBetaOfSameVersion() {
        assertTrue(AppUpdateChecker.isNewerVersion("v0.1.0", "0.1.0-beta.16"))
        assertFalse(AppUpdateChecker.isNewerVersion("v0.1.0-beta.16", "0.1.0"))
    }

    @Test
    fun invalidVersionTagsAreNeverTreatedAsUpdates() {
        assertFalse(AppUpdateChecker.isNewerVersion("main", "0.1.0-beta.16"))
        assertFalse(AppUpdateChecker.isNewerVersion("v0.1.0-beta.17/evil", "0.1.0-beta.16"))
    }


    @Test
    fun betaReleaseDebugArm64AssetIsAccepted() {
        val assets = listOf("UBAHVO-0.1.0-beta.17-arm64-v8a-debug.apk" to 12_345L)
        assertTrue(AppUpdateChecker.hasSupportedApkAsset(assets, "v0.1.0-beta.17"))
    }

    @Test
    fun productionArm64AssetWithoutDebugSuffixIsAccepted() {
        val assets = listOf("UBAHVO-0.1.0-arm64-v8a.apk" to 12_345L)
        assertTrue(AppUpdateChecker.hasSupportedApkAsset(assets, "0.1.0"))
    }

    @Test
    fun productionUniversalApkIsAcceptedForAllSupportedAbis() {
        val assets = listOf("UBAHVO-0.1.0-universal.apk" to 15_000L)
        assertTrue(AppUpdateChecker.hasSupportedApkAsset(assets, "0.1.0"))
    }

    @Test
    fun betaUniversalApkIsAccepted() {
        val assets = listOf("UBAHVO-0.1.0-beta.17-universal-debug.apk" to 15_000L)
        assertTrue(AppUpdateChecker.hasSupportedApkAsset(assets, "v0.1.0-beta.17"))
    }

    @Test
    fun rejectsUnrelatedWrongVersionOrEmptyApkAssets() {
        val assets = listOf(
            "OTHER-0.1.0-arm64-v8a.apk" to 12_345L,
            "UBAHVO-0.1.0-beta.16-arm64-v8a.apk" to 12_345L,
            "UBAHVO-0.1.0-arm64-v8a.apk" to 0L
        )
        assertFalse(AppUpdateChecker.hasSupportedApkAsset(assets, "0.1.0"))
    }

    @Test
    fun assetMatchingIsCaseInsensitiveButRequiresExactName() {
        val accepted = listOf("ubahvo-0.1.0-arm64-v8a.apk" to 100L)
        val lookalike = listOf("UBAHVO-0.1.0-arm64-v8a.apk.attacker" to 100L)
        assertTrue(AppUpdateChecker.hasSupportedApkAsset(accepted, "0.1.0"))
        assertFalse(AppUpdateChecker.hasSupportedApkAsset(lookalike, "0.1.0"))
    }


    @Test
    fun parsesBetaUniversalReleaseFromGitHubJson() {
        val json = """
            [{
              "tag_name": "v0.1.0-beta.17",
              "draft": false,
              "html_url": "https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.17",
              "assets": [{"name":"UBAHVO-0.1.0-beta.17-universal-debug.apk","size":15000000}]
            }]
        """.trimIndent()

        assertTrue(
            AppUpdateChecker.findNewerRelease(json, "0.1.0-beta.16") ==
                AppUpdateInfo(
                    "v0.1.0-beta.17",
                    "0.1.0-beta.17",
                    "https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.17"
                )
        )
    }

    @Test
    fun selectsStableReleaseOverBetaWhenBothAreNewer() {
        val json = """
            [
              {
                "tag_name":"v0.1.0-beta.19",
                "draft":false,
                "html_url":"https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.19",
                "assets":[{"name":"UBAHVO-0.1.0-beta.19-arm64-v8a-debug.apk","size":1000}]
              },
              {
                "tag_name":"v0.1.0",
                "draft":false,
                "html_url":"https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0",
                "assets":[{"name":"UBAHVO-0.1.0-universal.apk","size":2000}]
              }
            ]
        """.trimIndent()

        assertTrue(
            AppUpdateChecker.findNewerRelease(json, "0.1.0-beta.18")?.tag == "v0.1.0"
        )
    }

    @Test
    fun skipsDraftInvalidUrlAndUnsupportedAssetReleases() {
        val json = """
            [
              {
                "tag_name":"v0.1.0-beta.20","draft":true,
                "html_url":"https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.20",
                "assets":[{"name":"UBAHVO-0.1.0-beta.20-universal-debug.apk","size":1000}]
              },
              {
                "tag_name":"v0.1.0-beta.21","draft":false,
                "html_url":"https://evil.example/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.21",
                "assets":[{"name":"UBAHVO-0.1.0-beta.21-universal-debug.apk","size":1000}]
              },
              {
                "tag_name":"v0.1.0-beta.22","draft":false,
                "html_url":"https://github.com/RidTheWann/UBAHVO-Project/releases/tag/v0.1.0-beta.22",
                "assets":[{"name":"OTHER-0.1.0-beta.22-universal-debug.apk","size":1000}]
              }
            ]
        """.trimIndent()

        assertTrue(AppUpdateChecker.findNewerRelease(json, "0.1.0-beta.19") == null)
    }

    @Test
    fun rejectsNonArrayMalformedAndTrailingJsonResponses() {
        listOf(
            """{"tag_name":"v0.1.0-beta.17"}""",
            """[{"tag_name":""",
            """[] {}"""
        ).forEach { json ->
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                AppUpdateChecker.findNewerRelease(json, "0.1.0-beta.16")
            }
        }
    }

}
