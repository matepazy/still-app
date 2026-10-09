package app.still

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.still.data.settings.ThemePreference
import app.still.data.themes.InstalledTheme
import app.still.data.themes.ThemeAppearance
import app.still.ui.theme.communityStyle

/** Switches only the launcher entry; explicit MainActivity intents and widgets stay stable. */
object SeasonalLauncherIcon {
    @Synchronized
    fun sync(context: Context, theme: ThemePreference, customTheme: InstalledTheme? = null) {
        val manager = context.packageManager
        val componentPackage = MainActivity::class.java.packageName
        val customAlias = customTheme?.takeIf { it.customIcon }?.let { installed ->
            val style = communityStyle(context, installed)
            val palette = if (installed.appearance == ThemeAppearance.Light) installed.content.theme.light else installed.content.theme.dark
            CustomLauncherIcon.closest(palette.getValue("primary").resolve(style.values),
                palette.getValue("background").resolve(style.values)).alias
        }
        val activeAlias = customAlias ?: when (theme) {
            ThemePreference.Fall -> "FallLauncher"
            ThemePreference.Halloween -> "HalloweenLauncher"
            else -> "DefaultLauncher"
        }
        val components = (listOf("DefaultLauncher", "FallLauncher", "HalloweenLauncher") + CustomLauncherIcon.aliases)
            .map { ComponentName(context.packageName, "$componentPackage.$it") }
        val active = ComponentName(context.packageName, "$componentPackage.$activeAlias")
        if (manager.getComponentEnabledSetting(active) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            manager.setComponentEnabledSetting(
                active,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        components.filterNot { it == active }.forEach { inactive ->
            val setting = manager.getComponentEnabledSetting(inactive)
            // Custom and seasonal aliases are disabled in the manifest by default.
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && inactive.className.endsWith(".DefaultLauncher"))) {
                manager.setComponentEnabledSetting(
                    inactive,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
