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
}
