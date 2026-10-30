package com.ridthewann.ubahvo.updates

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateCheckJobService : JobService() {
    private class JobRun(val params: JobParameters) {
        val cancelled = AtomicBoolean(false)

        @Volatile
        var worker: Thread? = null
    }

    @Volatile
    private var runningJob: JobRun? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val run = JobRun(params)
        run.worker = Thread({
            try {
                val update = AppUpdateChecker.checkForUpdate(applicationContext)
                if (update != null) {
                    // Serialize the final notification with onStopJob so a stopped job cannot
                    // post a notification after the scheduler has asked it to shut down.
                    synchronized(run) {
                        if (!run.cancelled.get() &&
                            AppUpdateNotifications.show(applicationContext, update)
                        ) {
                            AppUpdateChecker.markNotified(applicationContext, update.tag)
                        }
                    }
                }
            } catch (error: Exception) {
                if (!run.cancelled.get()) {
                    Log.w(TAG, "Scheduled app-update check failed; it will retry on a later run", error)
                }
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                if (!run.cancelled.get()) {
                    Log.e(TAG, "Non-fatal JVM error in scheduled app-update check", error)
                }
            } finally {
                // Android has already released the job when onStopJob is called. Do not call
                // jobFinished for a run that the system has stopped.
                synchronized(run) {
                    if (!run.cancelled.get()) jobFinished(params, false)
                }
                if (runningJob === run) runningJob = null
            }
        }, "ubahvo-app-update-check").apply {
            isDaemon = true
        }
        runningJob = run
        run.worker?.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val run = runningJob ?: return true
        synchronized(run) {
            run.cancelled.set(true)
        }
        run.worker?.interrupt()
        if (runningJob === run) runningJob = null
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
