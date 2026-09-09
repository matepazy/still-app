package app.still.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.still.data.settings.ThemePreference
import app.still.domain.model.AppDetail
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.UsageDashboard
import app.still.ui.appdetail.AppDetailScreen
import app.still.ui.appdetail.AppDetailTopBar
import app.still.ui.apps.AppsScreen
import app.still.ui.apps.AppsTopBar
import app.still.ui.components.StillMark
import app.still.ui.onboarding.OnboardingScreen
import app.still.ui.settings.SettingsScreen
import app.still.ui.settings.SettingsTopBar
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.StillTheme
import app.still.ui.timeline.TimelineScreen
import app.still.ui.timeline.TimelineTopBar
import app.still.ui.today.TodayScreen
import app.still.ui.today.TodayTopBar
import java.time.Duration

private const val TodayRoute = "today"
private const val TimelineRoute = "timeline"
private const val AppsRoute = "apps"
private const val SettingsRoute = "settings"
private const val AppDetailRoute = "app/{packageName}"

@Composable
fun StillApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    StillTheme(
        themePreference = if (settings?.onboardingComplete == false) ThemePreference.Dark else settings?.theme ?: ThemePreference.System,
        useDynamicColors = settings?.useDynamicColors ?: false,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (settings == null) {
                LoadingScreen()
            } else if (!settings.onboardingComplete) {
                OnboardingScreen(
                    usageState = state.usage,
                    onOpenSettings = { context.startActivity(viewModel.usageSettingsIntent()) },
                    onComplete = viewModel::completeOnboarding,
                )
            } else {
                when (val usage = state.usage) {
                    UsageUiState.Loading -> LoadingScreen()
                    UsageUiState.PermissionRequired -> PermissionRequiredScreen { context.startActivity(viewModel.usageSettingsIntent()) }
                    is UsageUiState.Error -> ErrorScreen(usage.message, viewModel::refresh)
                    is UsageUiState.Ready -> MainNavigation(usage.dashboard, settings, viewModel)
                }
            }
        }
    }
}

@Composable
private fun MainNavigation(
    dashboard: UsageDashboard,
    settings: app.still.data.settings.UserSettings,
    viewModel: MainViewModel,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val primaryRoutes = setOf(TodayRoute, TimelineRoute, AppsRoute)

    Scaffold(
        topBar = {
            when (currentRoute) {
                TodayRoute -> TodayTopBar { navController.navigate(SettingsRoute) }
                TimelineRoute -> TimelineTopBar { navController.navigate(SettingsRoute) }
                AppsRoute -> AppsTopBar { navController.navigate(SettingsRoute) }
                SettingsRoute -> SettingsTopBar { navController.popBackStack() }
                AppDetailRoute -> {
                    val packageName = backStackEntry?.arguments?.getString("packageName")
                    val title = dashboard.today.apps.firstOrNull { it.app.packageName == packageName }?.app?.label ?: "App"
                    AppDetailTopBar(title) { navController.popBackStack() }
                }
            }
        },
        bottomBar = {
            if (currentRoute in primaryRoutes) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    listOf(
                        Triple(TodayRoute, "Today", Icons.Default.Home),
                        Triple(TimelineRoute, "Timeline", Icons.Default.BarChart),
                        Triple(AppsRoute, "Apps", Icons.Default.Apps),
                    ).forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = null) },
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
        },
    ) { padding ->
        NavHost(navController, startDestination = TodayRoute, modifier = Modifier.padding(padding)) {
            composable(TodayRoute) {
                TodayScreen(dashboard, onDaylineClick = { navController.navigate(TimelineRoute) })
            }
            composable(TimelineRoute) { TimelineScreen(dashboard.today) }
            composable(AppsRoute) {
                AppsScreen(dashboard.today, onAppClick = { packageName -> navController.navigate("app/$packageName") })
            }
            composable(AppDetailRoute) { entry ->
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                buildAppDetail(packageName, dashboard)?.let { AppDetailScreen(it) }
                    ?: ErrorScreen("This app has no usage information today.") { navController.popBackStack() }
            }
            composable(SettingsRoute) {
                SettingsScreen(
                    settings = settings,
                    onThemeChange = viewModel::setTheme,
                    onDynamicChange = viewModel::setDynamicColors,
                    onTargetChange = viewModel::setDailyTargetMinutes,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}

private fun buildAppDetail(packageName: String, dashboard: UsageDashboard): AppDetail? {
    val usage = dashboard.today.apps.firstOrNull { it.app.packageName == packageName } ?: return null
    val daily = dashboard.history.sortedBy { it.date }.map { day ->
        val available = day.total > Duration.ZERO || day.unlocks > 0 || day.wakeups > 0
        DailyAppUsage(day.date, if (available) day.apps.firstOrNull { it.app.packageName == packageName }?.duration ?: Duration.ZERO else null)
    }
    val valid = daily.mapNotNull { it.duration }
    val average = valid.takeIf { it.isNotEmpty() }?.map { it.toMillis() }?.average()?.toLong()?.let(Duration::ofMillis)
    return AppDetail(
        usage = usage,
        dailyUsage = daily,
        averageDaily = average,
        sessions = dashboard.today.sessions.filter { session -> session.apps.any { it.app.packageName == packageName } },
    )
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun PermissionRequiredScreen(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            StillMark(size = 42.dp)
        }
        Text("No usage access yet", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = StillSpacing.large))
        Text(
            "To show your screen time, Still needs usage access permission.",
            modifier = Modifier.padding(top = StillSpacing.medium, bottom = StillSpacing.large),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(25.dp),
        ) { Text("Open settings") }
        Text("Learn more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = StillSpacing.large))
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(StillSpacing.large), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text("Usage data unavailable", style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(vertical = StillSpacing.medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
