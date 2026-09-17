package app.still.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import app.still.R
import app.still.data.settings.UserSettings
import app.still.data.settings.WidgetAppearance
import app.still.data.settings.WIDGET_PILL_RADIUS
import app.still.data.settings.parseWidgetColor
import app.still.data.settings.systemWidgetColors
import app.still.data.settings.widgetContrastColors

internal val Int.widgetBackgroundDrawable: Int
    get() = if (this == WIDGET_PILL_RADIUS) {
        R.drawable.widget_background_radius_pill
    } else when (((coerceIn(0, 32) + 2) / 4) * 4) {
        0 -> R.drawable.widget_background_radius_0
        4 -> R.drawable.widget_background_radius_4
        8 -> R.drawable.widget_background_radius_8
        12 -> R.drawable.widget_background_radius_12
        16 -> R.drawable.widget_background_radius_16
        20 -> R.drawable.widget_background_radius_20
        24 -> R.drawable.widget_background_radius_24
        28 -> R.drawable.widget_background_radius_28
        else -> R.drawable.widget_background_radius_32
    }

internal data class WidgetPalette(
    @param:ColorInt val background: Int,
    @param:ColorInt val primary: Int,
    @param:ColorInt val secondary: Int,
) {
    companion object {
        fun resolve(context: Context, settings: UserSettings): WidgetPalette {
            return resolve(context, settings.widgetAppearance, settings.widgetColor)
        }

        fun resolve(
            context: Context,
            appearance: WidgetAppearance,
            customColor: String?,
        ): WidgetPalette {
            parseWidgetColor(customColor)?.let { background ->
                val colors = widgetContrastColors(background)
                return WidgetPalette(colors.background, colors.foreground, colors.foreground)
            }
            val dark = when (appearance) {
                WidgetAppearance.System -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                WidgetAppearance.Light -> false
                WidgetAppearance.Dark -> true
            }
            if (appearance == WidgetAppearance.System) {
                systemWidgetColors(context, dark)?.let { colors ->
                    return WidgetPalette(colors.background, colors.primary, colors.secondary)
                }
            }
            return if (dark) {
                WidgetPalette(
                    ContextCompat.getColor(context, R.color.widget_background_dark),
                    ContextCompat.getColor(context, R.color.widget_primary_dark),
                    ContextCompat.getColor(context, R.color.widget_secondary_dark),
                )
            } else {
                WidgetPalette(
                    ContextCompat.getColor(context, R.color.widget_background_light),
                    ContextCompat.getColor(context, R.color.widget_primary_light),
                    ContextCompat.getColor(context, R.color.widget_secondary_light),
                )
            }
        }
    }
}

object WidgetUpdateDispatcher {
    fun updateAll(context: Context) {
        ScreenTimeWidgetProvider.updateAll(context)
        DaylineWidgetProvider.updateAll(context)
    }
}
