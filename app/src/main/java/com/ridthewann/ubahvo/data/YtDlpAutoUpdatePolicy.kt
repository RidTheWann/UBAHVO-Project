package com.ridthewann.ubahvo.data

/** Small, deterministic schedule policy for non-blocking yt-dlp support updates. */
internal object YtDlpAutoUpdatePolicy {
    const val SUCCESS_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val FAILURE_RETRY_INTERVAL_MS = 6L * 60L * 60L * 1000L

    fun isDue(nowMillis: Long, nextCheckAtMillis: Long): Boolean =
        nowMillis >= nextCheckAtMillis

    fun nextCheckAt(nowMillis: Long, succeeded: Boolean): Long =
        nowMillis + if (succeeded) SUCCESS_INTERVAL_MS else FAILURE_RETRY_INTERVAL_MS
}
