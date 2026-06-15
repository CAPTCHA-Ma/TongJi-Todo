package com.example.todo

import android.app.job.JobParameters
import android.app.job.JobService

class CanvasSyncJobService : JobService() {
    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        worker = Thread {
            val result = CanvasSync(applicationContext).syncPersistedPlannerStore(forceRefresh = false)
            val shouldRetry = result.status == CanvasSyncStatus.NetworkFailed
            jobFinished(params, shouldRetry)
        }.apply {
            name = "CanvasSyncJob"
            start()
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }
}
