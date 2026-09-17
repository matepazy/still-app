package app.still.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.still.MainActivity
import app.still.MainActivity.Companion.EXTRA_OPEN_TODAY
import app.still.R
import app.still.StillApplication
import app.still.data.settings.UserSettings
import app.still.data.settings.DaylineWidgetLabel
import app.still.domain.model.DailyUsage
import app.still.domain.model.DaylineKind
import app.still.ui.components.compactDuration
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DaylineWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.schedule(context)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        load(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        load(context, appWidgetManager, intArrayOf(appWidgetId))
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

    private fun load(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as StillApplication).container
                val settings = container.settingsRepository.settings.first()
                render(context, manager, ids, DaylineWidgetContent.Loading, settings)
                val content = if (!container.permissionManager.hasUsageAccess()) {
                    DaylineWidgetContent.PermissionRequired
                } else {
                    container.usageRepository.dashboard(settings.saveUsageHistory).fold(
                        onSuccess = { DaylineWidgetContent.Ready(it.today) },
                        onFailure = { DaylineWidgetContent.Unavailable },
                    )
                }
                render(context, manager, ids, content, settings)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        content: DaylineWidgetContent,
        settings: UserSettings,
    ) {
        val palette = WidgetPalette.resolve(context, settings.daylineWidgetAppearance, settings.daylineWidgetColor)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceIn(110, 300)
            val views = RemoteViews(context.packageName, R.layout.widget_dayline).apply {
                setImageViewResource(R.id.widget_dayline_background, settings.daylineWidgetCornerRadiusDp.widgetBackgroundDrawable)
                setInt(R.id.widget_dayline_background, "setColorFilter", palette.background)
                setInt(
                    R.id.widget_dayline_background,
                    "setImageAlpha",
                    settings.daylineWidgetBackgroundOpacityPercent.coerceIn(20, 100) * 255 / 100,
                )
                setTextColor(R.id.widget_dayline_label, palette.secondary)
                setTextColor(R.id.widget_dayline_status, palette.secondary)
                setInt(R.id.widget_dayline_refresh, "setColorFilter", palette.secondary)
                setTextViewText(R.id.widget_dayline_label, content.label(context, settings.daylineWidgetLabel))
                setViewVisibility(
                    R.id.widget_dayline_label,
                    if (content.showsLabel(settings.daylineWidgetLabel)) View.VISIBLE else View.GONE,
                )
                setViewVisibility(R.id.widget_dayline_refresh, if (settings.daylineWidgetShowRefresh) View.VISIBLE else View.GONE)
                when (content) {
                    is DaylineWidgetContent.Ready -> {
                        setViewVisibility(R.id.widget_dayline_chart, View.VISIBLE)
                        setViewVisibility(R.id.widget_dayline_status, View.GONE)
                        setImageViewBitmap(
                            R.id.widget_dayline_bar,
                            renderDaylineBitmap(context, widthDp, content.day, palette),
                        )
                    }
                    else -> {
                        setViewVisibility(R.id.widget_dayline_chart, View.GONE)
                        setViewVisibility(R.id.widget_dayline_status, View.VISIBLE)
                        setTextViewText(R.id.widget_dayline_status, content.status(context))
                    }
                }
                setContentDescription(R.id.widget_dayline_root, content.description(context))
                setOnClickPendingIntent(R.id.widget_dayline_root, openAppIntent(context))
                setOnClickPendingIntent(R.id.widget_dayline_refresh, refreshIntent(context))
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "app.still.widget.action.REFRESH_DAYLINE"
        private const val WALLPAPER_CHANGED_ACTION = "android.intent.action.WALLPAPER_CHANGED"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DaylineWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, DaylineWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }

        private fun openAppIntent(context: Context) = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TODAY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun refreshIntent(context: Context) = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, DaylineWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun renderDaylineBitmap(
    context: Context,
    widthDp: Int,
    day: DailyUsage,
    palette: WidgetPalette,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (widthDp * density).toInt().coerceIn(160, 1_200)
    val height = (18 * density).toInt().coerceAtLeast(18)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    val radius = 5f * density
    paint.color = ColorUtils.setAlphaComponent(palette.secondary, 34)
    canvas.drawRoundRect(bounds, radius, radius, paint)
    val clip = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(clip)
    val fullDayMillis = Duration.ofHours(24).toMillis().toFloat()
    day.dayline.forEach { segment ->
        val left = (Duration.between(day.rangeStart, segment.start).toMillis() / fullDayMillis).coerceIn(0f, 1f) * width
        val right = (Duration.between(day.rangeStart, segment.end).toMillis() / fullDayMillis).coerceIn(0f, 1f) * width
        if (right <= left) return@forEach
        paint.color = when (segment.kind) {
            DaylineKind.Active -> palette.primary
            DaylineKind.Inactive -> ColorUtils.setAlphaComponent(palette.secondary, 120)
        }
        canvas.drawRect(left, 0f, right, height.toFloat(), paint)
    }
    paint.strokeWidth = density
    paint.color = ColorUtils.setAlphaComponent(palette.background, 75)
    listOf(.25f, .5f, .75f).forEach { progress ->
        val x = width * progress
        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
    }
    val now = (Duration.between(day.rangeStart, day.rangeEnd).toMillis() / fullDayMillis).coerceIn(0f, 1f) * width
    paint.strokeWidth = 2f * density
    paint.color = palette.primary
    canvas.drawLine(now, 0f, now, height.toFloat(), paint)
    canvas.restore()
    return bitmap
}

private sealed interface DaylineWidgetContent {
    data object Loading : DaylineWidgetContent
    data class Ready(val day: DailyUsage) : DaylineWidgetContent
    data object PermissionRequired : DaylineWidgetContent
    data object Unavailable : DaylineWidgetContent

    fun label(context: Context, preference: DaylineWidgetLabel): String = when (this) {
        Loading, is Ready -> when (preference) {
            DaylineWidgetLabel.Dayline -> context.getString(R.string.widget_dayline)
            DaylineWidgetLabel.Today -> context.getString(R.string.widget_today)
            DaylineWidgetLabel.Hidden -> ""
        }
        PermissionRequired -> context.getString(R.string.widget_permission_needed)
        Unavailable -> context.getString(R.string.widget_unavailable)
    }

    fun showsLabel(preference: DaylineWidgetLabel) =
        this !is Loading && this !is Ready || preference != DaylineWidgetLabel.Hidden

    fun status(context: Context): String = when (this) {
        Loading -> context.getString(R.string.widget_loading)
        PermissionRequired, Unavailable -> context.getString(R.string.widget_open_still)
        is Ready -> ""
    }

    fun description(context: Context): String = when (this) {
        Loading -> context.getString(R.string.widget_dayline_loading_description)
        is Ready -> context.getString(R.string.widget_dayline_description_ready, day.total.compactDuration())
        PermissionRequired -> context.getString(R.string.widget_dayline_permission_description)
        Unavailable -> context.getString(R.string.widget_dayline_unavailable_description)
    }
}
