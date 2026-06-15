package com.example.todo

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log

object CanvasSyncScheduler {
    private const val JobId = 42_0615
    private const val SyncIntervalMillis = 60L * 60L * 1000L

    fun schedule(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            if (!CanvasSyncLocalStore(appContext).hasSavedToken()) return

            val scheduler = appContext.getSystemService(JobScheduler::class.java)
            val component = ComponentName(appContext, CanvasSyncJobService::class.java)
            val info = JobInfo.Builder(JobId, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(SyncIntervalMillis)
                .build()

            scheduler.schedule(info)
        }.onFailure { error ->
            Log.e(LogTag, "Failed to schedule Canvas sync job", error)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            context.applicationContext
                .getSystemService(JobScheduler::class.java)
                .cancel(JobId)
        }.onFailure { error ->
            Log.e(LogTag, "Failed to cancel Canvas sync job", error)
        }
    }

    private const val LogTag = "CanvasSyncScheduler"
}
