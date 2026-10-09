package app.still

import android.app.Application
import app.still.data.settings.SettingsRepository
import app.still.data.usage.UsagePermissionManager
import app.still.data.usage.UsageRepository
import app.still.data.usage.UsageStatsDataSource
import app.still.data.usage.UsageHistoryScheduler
import app.still.widget.WidgetUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class StillApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UsageHistoryScheduler.schedule(this)
        WidgetUpdateScheduler.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(container.settingsRepository.settings, container.communityThemes.state) { settings, themes ->
                settings.theme to themes
            }.filter { (_, themes) -> themes.loaded }.collect { (theme, themes) ->
                SeasonalLauncherIcon.sync(this@StillApplication, theme, themes.active)
            }
        }
    }
}

class AppContainer(val applicationContext: Application) {
    val permissionManager = UsagePermissionManager(applicationContext)
    val communityThemes = app.still.data.themes.CommunityThemeRepository(applicationContext)
    val settingsRepository = SettingsRepository(applicationContext)
    val usageRepository = UsageRepository(applicationContext, UsageStatsDataSource(applicationContext))
}
