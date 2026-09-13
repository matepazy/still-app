package app.still.data.usage

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.still.StillApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageHistoryJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val application = application as StillApplication
        if (!application.container.permissionManager.hasUsageAccess()) return false
        runningJob?.cancel()
        runningJob = scope.launch {
            try {
                application.container.usageRepository.syncHistory()
            } finally {
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

object UsageHistoryScheduler {
    private const val JOB_ID = 0x57111
    private const val TWELVE_HOURS_MS = 12L * 60L * 60L * 1_000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UsageHistoryJobService::class.java))
            .setPersisted(true)
            .setPeriodic(TWELVE_HOURS_MS)
            .build()
        scheduler.schedule(job)
    }
}
