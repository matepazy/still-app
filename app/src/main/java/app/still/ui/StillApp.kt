package app.still.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.still.data.settings.ThemePreference
import app.still.data.settings.AppCategory
import app.still.data.settings.LastDestination
import app.still.domain.model.AppDetail
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.UsageDashboard
import app.still.ui.appdetail.AppDetailScreen
import app.still.ui.appdetail.AppDetailTopBar
import app.still.ui.apps.AppsScreen
import app.still.ui.apps.AppsTopBar
import app.still.ui.components.StillMark
import app.still.ui.components.StillIcons
import app.still.ui.onboarding.OnboardingScreen
import app.still.ui.settings.SettingsScreen
import app.still.ui.settings.SettingsTopBar
import app.still.ui.settings.StoredDataScreen
import app.still.ui.settings.WidgetSettingsScreen
import app.still.ui.settings.WidgetSelectorScreen
import app.still.ui.settings.DaylineWidgetSettingsScreen
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.StillTheme
import app.still.ui.update.UpdateDetailsSheet
import app.still.ui.update.VersionOptInDialog
import app.still.update.UpdateState
import app.still.ui.timeline.TimelineScreen
import app.still.ui.timeline.TimelineTopBar
import app.still.ui.today.TodayScreen
import app.still.ui.today.TodayTopBar
import java.time.Duration
import java.time.LocalDate

private const val AppDetailRoute = "app/{packageName}"
private val PredictiveBackShape = RoundedCornerShape(28.dp)

@Composable
fun StillApp(
    viewModel: MainViewModel,
    navigationRequest: NavigationRequest? = null,
    onNavigationRequestHandled: (Int) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val storedDataSummary by viewModel.storedDataSummary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var activeUpdate by remember { mutableStateOf<UpdateState.UpdateAvailable?>(null) }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.UpdateAvailable) activeUpdate = updateState as UpdateState.UpdateAvailable
    }
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
                    onOpenRestrictedSettings = { context.startActivity(viewModel.restrictedSettingsIntent()) },
                    onOpenUsageSettings = { context.startActivity(viewModel.usageSettingsIntent()) },
                    onComplete = viewModel::completeOnboarding,
                )
            } else {
                when (val usage = state.usage) {
                    UsageUiState.Loading -> LoadingScreen()
                    UsageUiState.PermissionRequired -> PermissionRequiredScreen(
                        onOpenUsageSettings = { context.startActivity(viewModel.usageSettingsIntent()) },
                        onOpenAppInfo = { context.startActivity(viewModel.restrictedSettingsIntent()) },
                    )
                    is UsageUiState.Error -> ErrorScreen(usage.message, viewModel::refresh)
                    is UsageUiState.Ready -> MainNavigation(
                        usage.dashboard,
                        settings,
                        updateState,
                        viewModel,
                        storedDataSummary,
                        navigationRequest,
                        onNavigationRequestHandled,
                    )
                }
            }
        }

        if (settings?.onboardingComplete == true && settings.versionCheckEnabled == null) {
            VersionOptInDialog(onDecision = viewModel::setVersionCheckEnabled)
        }

        activeUpdate?.let { update ->
            if (settings?.onboardingComplete == true) {
                UpdateDetailsSheet(
                    update = update,
                    viewModel = viewModel,
                    onUpdateLater = {
                        activeUpdate = null
                        viewModel.remindAboutUpdateLater(update)
                    },
                )
            }
        }
    }
}

@Composable
private fun MainNavigation(
    dashboard: UsageDashboard,
    settings: app.still.data.settings.UserSettings,
    updateState: UpdateState,
    viewModel: MainViewModel,
    storedDataSummary: app.still.data.usage.StoredDataSummary?,
    navigationRequest: NavigationRequest?,
    onNavigationRequestHandled: (Int) -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val initialDestination = remember { navigationRequest?.destination ?: settings.lastDestination }
    val initialRequestId = remember { navigationRequest?.id }
    var navigationReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.setLastDestination(initialDestination)
        initialRequestId?.let(onNavigationRequestHandled)
        navigationReady = true
    }
    LaunchedEffect(navigationRequest) {
        if (navigationReady && navigationRequest != null && navigationRequest.id != initialRequestId) {
            navController.navigateTo(navigationRequest.destination)
            onNavigationRequestHandled(navigationRequest.id)
        }
    }
    DisposableEffect(navController, navigationReady) {
        if (!navigationReady) return@DisposableEffect onDispose { }
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            lastDestinationForRoute(destination.route)?.let(viewModel::setLastDestination)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val availableDays = remember(dashboard) {
        (listOf(dashboard.today) + dashboard.history).distinctBy { it.date }.sortedByDescending { it.date }
    }
    var selectedDateValue by rememberSaveable { mutableStateOf(dashboard.today.date.toString()) }
    LaunchedEffect(availableDays) {
        if (availableDays.none { it.date.toString() == selectedDateValue }) selectedDateValue = dashboard.today.date.toString()
    }
    val selectedDate = runCatching { LocalDate.parse(selectedDateValue) }.getOrDefault(dashboard.today.date)
    val selectedDay = availableDays.firstOrNull { it.date == selectedDate } ?: dashboard.today
    val selectDate: (LocalDate) -> Unit = { selectedDateValue = it.toString() }

    NavHost(
        navController = navController,
        startDestination = initialDestination.route,
        modifier = Modifier.fillMaxSize(),
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin.Center,
            )
        },
        predictivePopEnterTransition = { _ -> EnterTransition.None },
        predictivePopExitTransition = { _ ->
            scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin.Center,
            )
        },
    ) {
        composable(
            route = TodayRoute,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            DestinationScaffold(
                topBar = { TodayTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(TodayRoute, navController) },
            ) { padding ->
                TodayScreen(
                    dashboard,
                    onDaylineClick = { navController.navigate(TimelineRoute) },
                    onAppClick = { packageName ->
                        selectedDateValue = dashboard.today.date.toString()
                        navController.navigate("app/$packageName")
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(
            route = TimelineRoute,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            DestinationScaffold(
                topBar = { TimelineTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(TimelineRoute, navController) },
            ) { padding ->
                TimelineScreen(
                    selectedDay,
                    availableDays.map { it.date },
                    selectDate,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(
            route = AppsRoute,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            DestinationScaffold(
                topBar = { AppsTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(AppsRoute, navController) },
            ) { padding ->
                AppsScreen(
                    selectedDay,
                    onAppClick = { packageName -> navController.navigate("app/$packageName") },
                    availableDates = availableDays.map { it.date },
                    onDateSelected = selectDate,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(AppDetailRoute) { entry ->
            val packageName = entry.arguments?.getString("packageName").orEmpty()
            val title = selectedDay.apps.firstOrNull { it.app.packageName == packageName }?.app?.label ?: "App"
            val suggestedCategory = remember(packageName) {
                AppCategory.forPackage(context.packageManager, packageName)
            }
            val appCategory = settings.appCategoryOverrides[packageName] ?: suggestedCategory
            DestinationScaffold(
                topBar = { AppDetailTopBar(title) { navController.popBackStack() } },
            ) { padding ->
                buildAppDetail(packageName, dashboard, selectedDate)?.let {
                    AppDetailScreen(
                        it,
                        modifier = Modifier.padding(padding),
                        onDateSelected = selectDate,
                        category = appCategory,
                        onCategorySelected = { category -> viewModel.setAppCategory(packageName, category) },
                    )
                }
                    ?: ErrorScreen(
                        "This app has no usage information for the selected day.",
                        navController::popBackStack,
                    )
            }
        }
        composable(SettingsRoute) {
            DestinationScaffold(
                topBar = {
                    SettingsTopBar {
                        if (!navController.popBackStack()) navController.navigate(TodayRoute)
                    }
                },
            ) { padding ->
                SettingsScreen(
                    settings = settings,
                    onThemeChange = viewModel::setTheme,
                    onDynamicChange = viewModel::setDynamicColors,
                    onRefresh = viewModel::refresh,
                    onWidgetClick = { navController.navigate(WidgetSettingsRoute) },
                    onStoredDataClick = { navController.navigate(StoredDataRoute) },
                    onSaveUsageHistoryChange = viewModel::setSaveUsageHistory,
                    updateState = updateState,
                    onVersionCheckChange = viewModel::setVersionCheckEnabled,
                    onUpdateChannelChange = viewModel::setUpdateChannel,
                    onCheckForUpdates = viewModel::triggerVersionCheck,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(StoredDataRoute) {
            LaunchedEffect(Unit) { viewModel.refreshStoredDataSummary() }
            DestinationScaffold(
                topBar = {
                    SettingsTopBar(
                        title = "Data stored on this device",
                        onBack = {
                            if (!navController.popBackStack()) navController.navigate(SettingsRoute)
                        },
                    )
                },
            ) { padding ->
                StoredDataScreen(
                    summary = storedDataSummary,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(WidgetSettingsRoute) {
            DestinationScaffold(
                topBar = {
                    SettingsTopBar(
                        title = "Widgets",
                        onBack = {
                            if (!navController.popBackStack()) navController.navigate(SettingsRoute)
                        },
                    )
                },
            ) { padding ->
                WidgetSelectorScreen(
                    settings = settings,
                    previewDay = dashboard.today,
                    onScreenTimeClick = { navController.navigate(ScreenTimeWidgetSettingsRoute) },
                    onDaylineClick = { navController.navigate(DaylineWidgetSettingsRoute) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(ScreenTimeWidgetSettingsRoute) {
            DestinationScaffold(
                topBar = {
                    SettingsTopBar(
                        title = "Screen time",
                        onBack = { navController.popBackStack() },
                        onReset = viewModel::resetWidgetSettings,
                    )
                },
            ) { padding ->
                WidgetSettingsScreen(
                    settings = settings,
                    widgetPreviewDay = dashboard.today,
                    onWidgetColorChange = viewModel::setWidgetColor,
                    onWidgetThemeChange = viewModel::setWidgetTheme,
                    onWidgetLabelChange = viewModel::setWidgetLabel,
                    onWidgetFontSizeChange = viewModel::setWidgetFontSize,
                    onWidgetFontStyleChange = viewModel::setWidgetFontStyle,
                    onWidgetShowRefreshChange = viewModel::setWidgetShowRefresh,
                    onWidgetCornerRadiusChange = viewModel::setWidgetCornerRadius,
                    onWidgetBackgroundOpacityChange = viewModel::setWidgetBackgroundOpacity,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(DaylineWidgetSettingsRoute) {
            DestinationScaffold(
                topBar = {
                    SettingsTopBar(
                        title = "Dayline",
                        onBack = { navController.popBackStack() },
                        onReset = viewModel::resetDaylineWidgetSettings,
                    )
                },
            ) { padding ->
                DaylineWidgetSettingsScreen(
                    settings = settings,
                    widgetPreviewDay = dashboard.today,
                    onWidgetColorChange = viewModel::setDaylineWidgetColor,
                    onWidgetThemeChange = viewModel::setDaylineWidgetTheme,
                    onWidgetLabelChange = viewModel::setDaylineWidgetLabel,
                    onWidgetShowRefreshChange = viewModel::setDaylineWidgetShowRefresh,
                    onWidgetCornerRadiusChange = viewModel::setDaylineWidgetCornerRadius,
                    onWidgetBackgroundOpacityChange = viewModel::setDaylineWidgetBackgroundOpacity,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private fun NavHostController.navigateTo(destination: LastDestination) {
    when (destination) {
        LastDestination.Today -> {
            val returnedHome = popBackStack(TodayRoute, inclusive = false)
            if (!returnedHome && currentDestination?.route != TodayRoute) {
                navigate(TodayRoute) {
                    popUpTo(graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        LastDestination.Timeline, LastDestination.Apps -> navigate(destination.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        LastDestination.Settings -> navigate(SettingsRoute) { launchSingleTop = true }
        LastDestination.WidgetSettings -> {
            navigate(SettingsRoute) { launchSingleTop = true }
            navigate(WidgetSettingsRoute) { launchSingleTop = true }
        }
        LastDestination.ScreenTimeWidgetSettings -> {
            navigate(SettingsRoute) { launchSingleTop = true }
            navigate(WidgetSettingsRoute) { launchSingleTop = true }
            navigate(ScreenTimeWidgetSettingsRoute) { launchSingleTop = true }
        }
        LastDestination.DaylineWidgetSettings -> {
            navigate(SettingsRoute) { launchSingleTop = true }
            navigate(WidgetSettingsRoute) { launchSingleTop = true }
            navigate(DaylineWidgetSettingsRoute) { launchSingleTop = true }
        }
    }
}

@Composable
private fun DestinationScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clip(PredictiveBackShape),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
private fun StillNavigationBar(currentRoute: String, navController: NavHostController) {
    NavigationBar(
        modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple(TodayRoute, "Today", StillIcons.Today),
            Triple(TimelineRoute, "Timeline", StillIcons.Timeline),
            Triple(AppsRoute, "Apps", StillIcons.Apps),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    val returnedHome = route == TodayRoute && navController.popBackStack(TodayRoute, inclusive = false)
                    if (!returnedHome) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = route != TodayRoute
                        }
                    }
                },
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

internal fun buildAppDetail(packageName: String, dashboard: UsageDashboard, selectedDate: LocalDate): AppDetail? {
    val allDays = (dashboard.history + dashboard.today).distinctBy { it.date }.sortedBy { it.date }
    val selectedDay = allDays.firstOrNull { it.date == selectedDate } ?: return null
    val knownUsage = allDays.asReversed().asSequence()
        .flatMap { it.apps.asSequence() }
        .firstOrNull { it.app.packageName == packageName }
        ?: return null
    val usage = selectedDay.apps.firstOrNull { it.app.packageName == packageName }
        ?: knownUsage.copy(duration = Duration.ZERO, opens = 0)
    val daysByDate = allDays.associateBy { it.date }
    val windowStart = dashboard.today.date.minusDays(6)
    val daily = (0L..6L).map { offset ->
        val date = windowStart.plusDays(offset)
        val day = daysByDate[date]
        val available = day != null && (day.total > Duration.ZERO || day.unlocks > 0 || day.wakeups > 0)
        DailyAppUsage(
            date,
            if (available) day.apps.firstOrNull { it.app.packageName == packageName }?.duration ?: Duration.ZERO else null,
        )
    }
    val valid = allDays.asReversed()
        .asSequence()
        .filter { it.date.isBefore(selectedDate) }
        .filter { it.total > Duration.ZERO || it.unlocks > 0 || it.wakeups > 0 }
        .map { day -> day.apps.firstOrNull { it.app.packageName == packageName }?.duration ?: Duration.ZERO }
        .take(APP_BASELINE_DAYS)
        .toList()
    val average = valid.takeIf { it.isNotEmpty() }?.map { it.toMillis() }?.average()?.toLong()?.let(Duration::ofMillis)
    return AppDetail(
        date = selectedDate,
        usage = usage,
        dailyUsage = daily,
        averageDaily = average,
        sessions = selectedDay.sessions
            .filter { session -> session.apps.any { it.app.packageName == packageName } }
            .sortedByDescending { it.start },
    )
}

private const val APP_BASELINE_DAYS = 14

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun PermissionRequiredScreen(
    onOpenUsageSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
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
            "Still needs Usage Access to calculate how long you use each app. Your screen-time history stays on this device.",
            modifier = Modifier.padding(top = StillSpacing.medium, bottom = StillSpacing.large),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenUsageSettings,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(25.dp),
        ) { Text("Open Usage Access") }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenAppInfo,
                modifier = Modifier.padding(top = StillSpacing.small).fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            ) { Text("Setting blocked? Open app info") }
        }
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
