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

}
