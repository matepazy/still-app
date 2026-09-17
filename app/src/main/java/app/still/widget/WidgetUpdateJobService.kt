package app.still.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

class WidgetUpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        WidgetUpdateDispatcher.updateAll(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

object WidgetUpdateScheduler {
    private const val JOB_ID = 0x57112
    private const val THIRTY_MINUTES_MS = 30L * 60L * 1_000L

    fun schedule(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val screenTimeProvider = ComponentName(context, ScreenTimeWidgetProvider::class.java)
        val daylineProvider = ComponentName(context, DaylineWidgetProvider::class.java)
        if (manager.getAppWidgetIds(screenTimeProvider).isEmpty() &&
            manager.getAppWidgetIds(daylineProvider).isEmpty()
        ) {
            cancel(context)
            return
        }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val existing = scheduler.getPendingJob(JOB_ID)
        if (existing?.intervalMillis == THIRTY_MINUTES_MS) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, WidgetUpdateJobService::class.java))
            .setPersisted(true)
            .setPeriodic(THIRTY_MINUTES_MS)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
