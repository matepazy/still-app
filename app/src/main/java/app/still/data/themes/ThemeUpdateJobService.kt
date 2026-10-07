package app.still.data.themes

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

class ThemeUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try { (application as StillApplication).container.communityThemes.checkForUpdates() }
            finally { if (isActive) jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
object ThemeUpdateScheduler {
    private const val JOB_ID = 0x57113
    fun sync(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!enabled) { scheduler.cancel(JOB_ID); return }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, ThemeUpdateJobService::class.java))
            .setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24 * 60 * 60 * 1000L).build())
    }
}
