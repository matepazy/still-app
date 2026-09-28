package app.still

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.still.data.settings.ThemePreference

/** Switches only the launcher entry; explicit MainActivity intents and widgets stay stable. */
object SeasonalLauncherIcon {
    fun sync(context: Context, theme: ThemePreference) {
        val manager = context.packageManager
        val componentPackage = MainActivity::class.java.packageName
        val components = mapOf(
            ThemePreference.System to ComponentName(context.packageName, "$componentPackage.DefaultLauncher"),
            ThemePreference.Fall to ComponentName(context.packageName, "$componentPackage.FallLauncher"),
            ThemePreference.Halloween to ComponentName(context.packageName, "$componentPackage.HalloweenLauncher"),
        )
        val active = components[theme] ?: components.getValue(ThemePreference.System)
        if (manager.getComponentEnabledSetting(active) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            manager.setComponentEnabledSetting(
                active,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        components.values.filterNot { it == active }.forEach { inactive ->
            if (manager.getComponentEnabledSetting(inactive) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                manager.setComponentEnabledSetting(
                    inactive,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
