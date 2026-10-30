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
        val assets = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("name", "UBAHVO-0.1.0-beta.17-arm64-v8a-debug.apk")
                .put("size", 12_345L)
        )
        assertTrue(AppUpdateChecker.hasSupportedArm64Apk(assets, "v0.1.0-beta.17"))
    }

    @Test
    fun productionArm64AssetWithoutDebugSuffixIsAccepted() {
        val assets = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("name", "UBAHVO-0.1.0-arm64-v8a.apk")
                .put("size", 12_345L)
        )
        assertTrue(AppUpdateChecker.hasSupportedArm64Apk(assets, "0.1.0"))
    }

    @Test
    fun versionlessProductionArm64AssetIsAccepted() {
        val assets = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("name", "UBAHVO-arm64-v8a.apk")
                .put("size", 12_345L)
        )
        assertTrue(AppUpdateChecker.hasSupportedArm64Apk(assets, "0.1.0"))
    }

    @Test
    fun rejectsUnrelatedWrongVersionOrEmptyArm64Assets() {
        val assets = org.json.JSONArray()
            .put(org.json.JSONObject().put("name", "OTHER-0.1.0-arm64-v8a.apk").put("size", 12_345L))
            .put(org.json.JSONObject().put("name", "UBAHVO-0.1.0-beta.16-arm64-v8a.apk").put("size", 12_345L))
            .put(org.json.JSONObject().put("name", "UBAHVO-0.1.0-arm64-v8a.apk").put("size", 0L))
        assertFalse(AppUpdateChecker.hasSupportedArm64Apk(assets, "0.1.0"))
    }

    @Test
    fun assetMatchingIsCaseInsensitiveButRequiresExactName() {
        val accepted = org.json.JSONArray().put(
            org.json.JSONObject().put("name", "ubahvo-0.1.0-arm64-v8a.apk").put("size", 100L)
        )
        val lookalike = org.json.JSONArray().put(
            org.json.JSONObject().put("name", "UBAHVO-0.1.0-arm64-v8a.apk.attacker").put("size", 100L)
        )
        assertTrue(AppUpdateChecker.hasSupportedArm64Apk(accepted, "0.1.0"))
        assertFalse(AppUpdateChecker.hasSupportedArm64Apk(lookalike, "0.1.0"))
    }

}
