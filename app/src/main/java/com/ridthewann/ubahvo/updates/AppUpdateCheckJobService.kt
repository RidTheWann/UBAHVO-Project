package com.ridthewann.ubahvo.updates

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

class AppUpdateCheckJobService : JobService() {
    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        worker = Thread({
            try {
                val update = AppUpdateChecker.checkForUpdate(applicationContext)
                if (update != null && AppUpdateNotifications.show(applicationContext, update)) {
                    AppUpdateChecker.markNotified(applicationContext, update.tag)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Scheduled app-update check failed; it will retry on a later run", error)
            } finally {
                jobFinished(params, false)
                worker = null
            }
        }, "ubahvo-app-update-check").apply {
            isDaemon = true
            start()
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }

    companion object {
        private const val TAG = "UBAHVO-Update"
        private const val JOB_ID = 1601
        private const val INTERVAL_MS = 24L * 60L * 60L * 1000L
        private const val FLEX_MS = 6L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, AppUpdateCheckJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(INTERVAL_MS, FLEX_MS)
                .build()
            scheduler.schedule(job)
        }
    }
}
