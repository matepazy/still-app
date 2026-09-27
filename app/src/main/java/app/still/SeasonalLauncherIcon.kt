package app.still

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.still.data.settings.ThemePreference

/** Switches only the launcher entry; explicit MainActivity intents and widgets stay stable. */
object SeasonalLauncherIcon {
    fun sync(context: Context, theme: ThemePreference) {
        val manager = context.packageManager
        val default = ComponentName(context.packageName, "${context.packageName}.DefaultLauncher")
        val fall = ComponentName(context.packageName, "${context.packageName}.FallLauncher")
        val active = if (theme == ThemePreference.Fall) fall else default
        val inactive = if (theme == ThemePreference.Fall) default else fall
        if (manager.getComponentEnabledSetting(active) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            manager.setComponentEnabledSetting(
                active,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (manager.getComponentEnabledSetting(inactive) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            manager.setComponentEnabledSetting(
                inactive,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
