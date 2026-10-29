package com.ridthewann.ubahvo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpAutoUpdatePolicyTest {
    @Test
    fun firstCheckIsDueWhenNoNextCheckHasBeenScheduled() {
        assertTrue(YtDlpAutoUpdatePolicy.isDue(nowMillis = 100L, nextCheckAtMillis = 0L))
    }

    @Test
    fun checkIsNotDueBeforeScheduledTime() {
        assertFalse(YtDlpAutoUpdatePolicy.isDue(nowMillis = 99L, nextCheckAtMillis = 100L))
        assertTrue(YtDlpAutoUpdatePolicy.isDue(nowMillis = 100L, nextCheckAtMillis = 100L))
    }

    @Test
    fun successWaitsOneDayAndFailureRetriesAfterSixHours() {
        assertEquals(86_400_000L + 100L, YtDlpAutoUpdatePolicy.nextCheckAt(100L, succeeded = true))
        assertEquals(21_600_000L + 100L, YtDlpAutoUpdatePolicy.nextCheckAt(100L, succeeded = false))
    }
}
