package app.still.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.still.MainActivity
import app.still.R
import app.still.StillApplication
import app.still.ui.components.compactDuration
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenTimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetManager, appWidgetIds, WidgetContent.Loading)
        load(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(context)
        }
    }

    private fun load(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as StillApplication
                val container = application.container
                val content = if (!container.permissionManager.hasUsageAccess()) {
                    WidgetContent.PermissionRequired
                } else {
                    container.usageRepository.dashboard().fold(
                        onSuccess = { WidgetContent.Ready(it.today.total) },
                        onFailure = { WidgetContent.Unavailable },
                    )
                }
                render(context, appWidgetManager, appWidgetIds, content)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        content: WidgetContent,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_screen_time).apply {
                setTextViewText(R.id.widget_label, content.label(context))
                setTextViewText(R.id.widget_value, content.value(context))
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.still.widget.action.REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ScreenTimeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val intent = Intent(context, ScreenTimeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ScreenTimeWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private sealed interface WidgetContent {
    data object Loading : WidgetContent
    data class Ready(val duration: Duration) : WidgetContent
    data object PermissionRequired : WidgetContent
    data object Unavailable : WidgetContent

    fun label(context: Context): String = when (this) {
        Loading, is Ready -> context.getString(R.string.widget_screen_time)
        PermissionRequired -> context.getString(R.string.widget_permission_needed)
        Unavailable -> context.getString(R.string.widget_unavailable)
    }

    fun value(context: Context): String = when (this) {
        Loading -> context.getString(R.string.widget_loading)
        is Ready -> duration.compactDuration()
        PermissionRequired -> context.getString(R.string.widget_open_still)
        Unavailable -> context.getString(R.string.widget_open_still)
    }
}
