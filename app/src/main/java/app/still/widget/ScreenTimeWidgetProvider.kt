package app.still.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.still.MainActivity
import app.still.MainActivity.Companion.EXTRA_OPEN_TODAY
import app.still.R
import app.still.StillApplication
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetFontStyle
import app.still.data.settings.WidgetLabel
import app.still.ui.components.compactDuration
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenTimeWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.schedule(context)
        super.onDisabled(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        load(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH ||
            intent.action == Intent.ACTION_CONFIGURATION_CHANGED ||
            intent.action == WALLPAPER_CHANGED_ACTION
        ) {
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
                val settings = container.settingsRepository.settings.first()
                render(context, appWidgetManager, appWidgetIds, WidgetContent.Loading, settings)
                val content = if (!container.permissionManager.hasUsageAccess()) {
                    WidgetContent.PermissionRequired
                } else {
                    container.usageRepository.dashboard(settings.saveUsageHistory).fold(
                        onSuccess = { WidgetContent.Ready(it.today.total) },
                        onFailure = { WidgetContent.Unavailable },
                    )
                }
                render(context, appWidgetManager, appWidgetIds, content, settings)
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
        settings: UserSettings,
    ) {
        val palette = WidgetPalette.resolve(context, settings)
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_screen_time).apply {
                setImageViewResource(R.id.widget_background, settings.widgetCornerRadiusDp.widgetBackgroundDrawable)
                setInt(R.id.widget_background, "setColorFilter", palette.background)
                setInt(
                    R.id.widget_background,
                    "setImageAlpha",
                    settings.widgetBackgroundOpacityPercent.coerceIn(20, 100) * 255 / 100,
                )
                setTextColor(R.id.widget_label, palette.secondary)
                setInt(R.id.widget_refresh, "setColorFilter", palette.secondary)
                setTextViewText(R.id.widget_label, content.label(context, settings.widgetLabel))
                VALUE_VIEW_IDS.forEach { viewId ->
                    setTextColor(viewId, palette.primary)
                    setTextViewText(viewId, content.value(context))
                    setTextViewTextSize(viewId, android.util.TypedValue.COMPLEX_UNIT_SP, settings.widgetFontSize.valueSp)
                    setViewVisibility(viewId, if (viewId == settings.widgetFontStyle.valueViewId) View.VISIBLE else View.GONE)
                }
                setViewVisibility(R.id.widget_label, if (content.showsLabel(settings.widgetLabel)) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_refresh, if (settings.widgetShowRefresh) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.still.widget.action.REFRESH"
        private const val WALLPAPER_CHANGED_ACTION = "android.intent.action.WALLPAPER_CHANGED"
        private val VALUE_VIEW_IDS = intArrayOf(
            R.id.widget_value_regular,
            R.id.widget_value_medium,
            R.id.widget_value_bold,
        )

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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TODAY, true)
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

private val WidgetFontStyle.valueViewId: Int
    get() = when (this) {
        WidgetFontStyle.Regular -> R.id.widget_value_regular
        WidgetFontStyle.Medium -> R.id.widget_value_medium
        WidgetFontStyle.Bold -> R.id.widget_value_bold
    }

private sealed interface WidgetContent {
    data object Loading : WidgetContent
    data class Ready(val duration: Duration) : WidgetContent
    data object PermissionRequired : WidgetContent
    data object Unavailable : WidgetContent

    fun label(context: Context, preference: WidgetLabel): String = when (this) {
        Loading, is Ready -> when (preference) {
            WidgetLabel.ScreenTime -> context.getString(R.string.widget_screen_time)
            WidgetLabel.Today -> context.getString(R.string.widget_today)
            WidgetLabel.Hidden -> ""
        }
        PermissionRequired -> context.getString(R.string.widget_permission_needed)
        Unavailable -> context.getString(R.string.widget_unavailable)
    }

    fun showsLabel(preference: WidgetLabel): Boolean = this !is Loading && this !is Ready || preference != WidgetLabel.Hidden

    fun value(context: Context): String = when (this) {
        Loading -> context.getString(R.string.widget_loading)
        is Ready -> duration.compactDuration()
        PermissionRequired -> context.getString(R.string.widget_open_still)
        Unavailable -> context.getString(R.string.widget_open_still)
    }
}
