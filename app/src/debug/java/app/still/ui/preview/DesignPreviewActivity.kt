package app.still.ui.preview

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.still.data.settings.AppCategory
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.domain.model.StatisticsDay
import app.still.domain.model.StatisticsPeriod
import app.still.domain.statistics.StatisticsCalculator
import app.still.ui.PermissionRequiredScreen
import app.still.ui.UsageUiState
import app.still.ui.appdetail.AppDetailScreen
import app.still.ui.appdetail.AppDetailTopBar
import app.still.ui.apps.AppsScreen
import app.still.ui.apps.AppsTopBar
import app.still.ui.components.StillIcons
import app.still.ui.onboarding.OnboardingScreen
import app.still.ui.settings.SettingsScreen
import app.still.ui.settings.SettingsTopBar
import app.still.ui.settings.ThemeSettingsScreen
import app.still.ui.settings.WidgetSettingsScreen
import app.still.ui.settings.WidgetSelectorScreen
import app.still.ui.statistics.StatisticsPeriodSelector
import app.still.ui.statistics.StatisticsTopBar
import app.still.ui.statistics.statisticsContent
import app.still.ui.theme.StillTheme
import app.still.ui.timeline.TimelineScreen
import app.still.ui.timeline.TimelineTopBar
import app.still.ui.today.TodayScreen
import app.still.ui.today.TodayTopBar
import java.time.Duration

class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "today"
        val light = intent.getBooleanExtra("light", false)
        setContent {
            val repository = (application as app.still.StillApplication).container.communityThemes
            val state by repository.state.collectAsState()
            val customStyle = if (screen.startsWith("custom-")) state.active?.let { app.still.ui.theme.communityStyle(this, it) } else null
            StillTheme(if (screen.startsWith("custom-")) ThemePreference.Fall else if (light) ThemePreference.Light else ThemePreference.Dark, communityStyle = customStyle) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { DesignScreen(screen.removePrefix("custom-")) }
            }
        }
    }
}

@Composable
private fun DesignScreen(screen: String) {
    var activeScreen by remember(screen) { mutableStateOf(screen) }
    var editingId by remember { mutableStateOf<String?>(null) }
    when (activeScreen) {
        "onboarding" -> OnboardingScreen(UsageUiState.PermissionRequired, { false }, { Intent(Settings.ACTION_SETTINGS) }, { Intent(Settings.ACTION_SETTINGS) }, { false }, {})
        "permission" -> PermissionRequiredScreen({ false }, { Intent(Settings.ACTION_SETTINGS) }, { Intent(Settings.ACTION_SETTINGS) }, { false }, {})
        "detail" -> Scaffold(topBar = { AppDetailTopBar("Instagram", {}) }) { padding ->
            AppDetailScreen(PreviewFixtures.appDetail, Modifier.padding(padding))
        }
        "theme-creator" -> app.still.ui.theme.TrustedThemeControls { app.still.ui.settings.ThemeCreatorScreen(onClose = { activeScreen = "themes" }, themeId = editingId) }
        "themes" -> app.still.ui.theme.TrustedThemeControls { Scaffold(topBar = { SettingsTopBar(title = "Theme", onBack = { activeScreen = "settings" }) }) { padding ->
            ThemeSettingsScreen(ThemePreference.Dark, null, {}, {}, Modifier.padding(padding), onCreateTheme = { editingId = null; activeScreen = "theme-creator" },
                onEditTheme = { editingId = it; activeScreen = "theme-creator" })
        } }
        "widget" -> Scaffold(topBar = { SettingsTopBar {} }) { padding ->
            WidgetSettingsScreen(
                UserSettings(theme = ThemePreference.Dark), PreviewFixtures.today,
                {}, {}, {}, {}, {}, {}, {}, {}, {}, Modifier.padding(padding),
            )
        }
        "widgets" -> Scaffold(topBar = { SettingsTopBar(title = "Widgets") {} }) { padding ->
            WidgetSelectorScreen(UserSettings(), PreviewFixtures.today, {}, {}, Modifier.padding(padding))
        }
        "settings" -> app.still.ui.theme.TrustedThemeControls { Scaffold(topBar = { SettingsTopBar {} }) { padding ->
            SettingsScreen(
                settings = UserSettings(onboardingComplete = true, theme = ThemePreference.Dark),
                onThemeClick = { activeScreen = "themes" },
                communityThemeTitle = app.still.ui.theme.LocalCommunityStyle.current?.installed?.content?.theme?.title,
                onRefresh = {},
                onWidgetClick = {},
                onStoredDataClick = {},
                modifier = Modifier.padding(padding),
            )
        } }
        else -> PreviewMainScaffold(screen)
    }
}

@Composable
private fun PreviewMainScaffold(screen: String) {
    Scaffold(
        topBar = {
            when (screen) {
                "timeline" -> TimelineTopBar {}
                "apps" -> AppsTopBar {}
                "statistics" -> StatisticsTopBar {}
                else -> TodayTopBar {}
            }
        },
        bottomBar = { PreviewNavigationBar(screen) },
    ) { padding ->
        when (screen) {
            "timeline" -> TimelineScreen(PreviewFixtures.today, modifier = Modifier.padding(padding))
            "apps" -> AppsScreen(PreviewFixtures.today, {}, modifier = Modifier.padding(padding))
            "statistics" -> PreviewStatistics(Modifier.padding(padding))
            else -> TodayScreen(PreviewFixtures.dashboard, {}, {}, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PreviewNavigationBar(selected: String) {
    NavigationBar(
        modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple("today", "Today", StillIcons.Today),
            Triple("timeline", "Timeline", StillIcons.Timeline),
            Triple("apps", "Apps", StillIcons.Apps),
            Triple("statistics", "Statistics", StillIcons.Statistics),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = selected == route,
                onClick = {},
                icon = { Icon(painterResource(icon), contentDescription = null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun PreviewStatistics(modifier: Modifier) {
    var period by remember { mutableStateOf(StatisticsPeriod.Week) }
    val range = period.range(PreviewFixtures.today.date)
    val days = remember(range) {
        (0 until range.days).map { index ->
            StatisticsDay(
                date = range.start.plusDays(index), screenTime = Duration.ofMinutes(90 + index % 7 * 25),
                checkIns = 27, quickChecks = 12, unlocks = 30, wakeups = 35,
                longestBreak = Duration.ofMinutes(78), longestSession = Duration.ofMinutes(24),
                firstUseMinute = 560, lastUseMinute = 1120,
                hourlyMillis = List(24) { hour -> if (hour in 9..19) (hour % 4 + 1) * 240_000L else 0L },
                appSwitches = 18, apps = PreviewFixtures.today.apps,
                sessionCount = 12, sessionTotalMillis = 9_600_000L,
            )
        }
    }
    val summary = remember(days, period) {
        StatisticsCalculator.calculate(range, days, emptyList(), { AppCategory.Other }, period)
    }
    LazyColumn(modifier.padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(20.dp))
            StatisticsPeriodSelector(period, range, days.map { it.date }, { period = it }, {})
            Spacer(Modifier.height(20.dp))
        }
        statisticsContent(summary, period)
        item { Spacer(Modifier.height(24.dp)) }
    }
}
