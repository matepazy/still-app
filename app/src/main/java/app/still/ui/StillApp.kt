package app.still.ui

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import app.still.domain.model.AppInfo
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.UsageDashboard
import app.still.ui.appdetail.AppDetailScreen
import app.still.ui.appdetail.AppDetailTopBar
import app.still.ui.apps.AppsScreen
import app.still.ui.apps.AppsTopBar
import app.still.ui.components.StillMark
import app.still.ui.components.StillIcons
import app.still.ui.components.LoadingSkeleton
import app.still.ui.onboarding.OnboardingScreen
import app.still.ui.onboarding.UsageAccessFlow
import app.still.ui.settings.SettingsScreen
import app.still.ui.settings.VersionArtworkScreen
import app.still.ui.settings.IssueReportBrowser
import app.still.ui.settings.ThemeSettingsScreen
import app.still.ui.settings.SettingsTopBar
import app.still.ui.settings.StoredDataScreen
import app.still.ui.settings.ArchiveMigrationSheet
import app.still.ui.settings.WidgetSettingsScreen
import app.still.ui.settings.WidgetSelectorScreen
import app.still.ui.settings.DaylineWidgetSettingsScreen
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.StillTheme
import app.still.ui.update.UpdateDetailsSheet
import app.still.ui.update.VersionOptInSheet
import app.still.update.UpdateState
import app.still.ui.timeline.TimelineScreen
import app.still.ui.timeline.TimelineTopBar
import app.still.ui.today.TodayScreen
import app.still.ui.today.TodayTopBar
import app.still.ui.statistics.StatisticsScreen
import app.still.ui.statistics.StatisticsTopBar
import app.still.ui.statistics.StatisticsViewModel
import app.still.StillApplication
import app.still.ui.compare.CompareScreen
import app.still.ui.compare.CompareTopBar
import app.still.ui.compare.CompareViewModel
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate

private const val AppDetailRoute = "app/{packageName}"
private const val VersionArtworkRoute = "settings/version-artwork"
private val PredictiveBackShape = RoundedCornerShape(28.dp)
private val PredictiveBackEasing = CubicBezierEasing(0.15f, 0f, 0.15f, 1f)
private val BackHandoffEasing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)
private const val PredictiveBackDurationMillis = 240
private const val PredictiveBackScale = 0.85f
private const val BackPreviewDurationMillis = 96
private const val BackHandoffStartMillis = 120
private const val BackHandoffDurationMillis = PredictiveBackDurationMillis - BackHandoffStartMillis
private const val BackPreviewAlpha = 0.75f

private const val ForwardNavigationDurationMillis = 240
private val ForwardNavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val TopLevelRoutes = setOf(TodayRoute, TimelineRoute, AppsRoute, StatisticsRoute)

private fun forwardDestinationEnter(offsetPx: Int): EnterTransition =
    fadeIn(tween(ForwardNavigationDurationMillis, easing = ForwardNavigationEasing)) +
        slideInHorizontally(
            animationSpec = tween(ForwardNavigationDurationMillis, easing = ForwardNavigationEasing),
        ) { offsetPx }

private fun forwardDestinationExit(): ExitTransition =
    fadeOut(tween(ForwardNavigationDurationMillis, easing = ForwardNavigationEasing)) +
        scaleOut(
            animationSpec = tween(ForwardNavigationDurationMillis, easing = ForwardNavigationEasing),
            targetScale = 0.98f,
        )

private class BackAnimationState {
    var value: Int = BackEventCompat.EDGE_LEFT
    var tabNavigation: Boolean = false
}

private fun backPreviewOffset(width: Int, marginPx: Int, swipeEdge: Int): Int {
    val shift = (width * (1f - PredictiveBackScale) / 2f - marginPx)
        .toInt().coerceAtLeast(0)
    return if (swipeEdge == BackEventCompat.EDGE_LEFT) shift else -shift
}

// Use the same seekable timeline for preview and completion. Navigation can retain the
// predictive transition on release, so a separate pop-only fade would be skipped.
// Shrink early, hold the two small pages, then crossfade and expand the destination.
private fun backDestinationExit(swipeEdge: Int, marginPx: Int): ExitTransition =
    scaleOut(
        animationSpec = tween(BackPreviewDurationMillis, easing = PredictiveBackEasing),
        targetScale = PredictiveBackScale,
        transformOrigin = TransformOrigin.Center,
    ) + slideOutHorizontally(
        animationSpec = tween(BackPreviewDurationMillis, easing = PredictiveBackEasing),
    ) { width -> backPreviewOffset(width, marginPx, swipeEdge) } + fadeOut(
        animationSpec = tween(
            BackHandoffDurationMillis,
            delayMillis = BackHandoffStartMillis,
            easing = BackHandoffEasing,
        ),
    )

private fun backDestinationEnter(swipeEdge: Int, marginPx: Int): EnterTransition =
    scaleIn(
        animationSpec = tween(
            BackHandoffDurationMillis,
            delayMillis = BackHandoffStartMillis,
            easing = BackHandoffEasing,
        ),
        initialScale = PredictiveBackScale,
        transformOrigin = TransformOrigin.Center,
    ) + slideInHorizontally(
        animationSpec = tween(
            BackHandoffDurationMillis,
            delayMillis = BackHandoffStartMillis,
            easing = BackHandoffEasing,
        ),
    ) { width -> -backPreviewOffset(width, marginPx, swipeEdge) } + fadeIn(
        animationSpec = tween(
            BackHandoffDurationMillis,
            delayMillis = BackHandoffStartMillis,
            easing = BackHandoffEasing,
        ),
        initialAlpha = BackPreviewAlpha,
    )

@Composable
fun StillApp(
    viewModel: MainViewModel,
    navigationRequest: NavigationRequest? = null,
    onContentReady: () -> Unit = {},
    onNavigationRequestHandled: (Int) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Do not compose a fallback theme while DataStore restores the user's selection.
    val settings = state.settings ?: return
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val storedDataSummary by viewModel.storedDataSummary.collectAsStateWithLifecycle()
    val archiveMigrationNotice by viewModel.archiveMigrationNotice.collectAsStateWithLifecycle()
    val archiveRestoreState by viewModel.archiveRestoreState.collectAsStateWithLifecycle()
    val archiveUpgradeState by viewModel.archiveUpgradeState.collectAsStateWithLifecycle()
    val archiveBackupDeleteState by viewModel.archiveBackupDeleteState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manageBeta by remember { mutableStateOf(false) }
    var activeUpdate by remember { mutableStateOf<UpdateState.UpdateAvailable?>(null) }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.UpdateAvailable) activeUpdate = updateState as UpdateState.UpdateAvailable
    }
    StillTheme(
        themePreference = if (!settings.onboardingComplete) ThemePreference.Dark else settings.theme,
    ) {
        SideEffect(onContentReady)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!settings.onboardingComplete) {
                OnboardingScreen(
                    usageState = state.usage,
                    hasUsageAccess = viewModel::hasUsageAccess,
                    usageSettingsIntent = viewModel::usageSettingsIntent,
                    appInfoIntent = viewModel::restrictedSettingsIntent,
                    installedFromApk = viewModel::installedFromApk,
                    onComplete = viewModel::completeOnboarding,
                )
            } else {
                when (val usage = state.usage) {
                    UsageUiState.Loading -> LoadingScreen()
                    UsageUiState.PermissionRequired -> PermissionRequiredScreen(
                        hasUsageAccess = viewModel::hasUsageAccess,
                        usageSettingsIntent = viewModel::usageSettingsIntent,
                        appInfoIntent = viewModel::restrictedSettingsIntent,
                        installedFromApk = viewModel::installedFromApk,
                        onGranted = viewModel::onResume,
                    )
                    is UsageUiState.Error -> ErrorScreen(usage.message, viewModel::refresh)
                    is UsageUiState.Ready -> MainNavigation(
                        usage.dashboard,
                        settings,
                        updateState,
                        viewModel,
                        storedDataSummary,
                        archiveRestoreState,
                        archiveUpgradeState,
                        archiveBackupDeleteState,
                        navigationRequest,
                        onNavigationRequestHandled,
                        onManageBeta = { manageBeta = true },
                    )
                }
            }
        }

        archiveMigrationNotice?.let { notice ->
            if (settings.onboardingComplete) {
                ArchiveMigrationSheet(notice, viewModel::acknowledgeArchiveMigration)
            }
        }

        if (archiveMigrationNotice == null && settings.onboardingComplete && settings.versionCheckEnabled == null) {
            VersionOptInSheet(onDecision = viewModel::setVersionCheckEnabled)
        }

        if (manageBeta && settings.updateChannel == "pre-release") {
            app.still.ui.update.ManageBetaDrawer(
                viewModel = viewModel,
                onDismiss = { manageBeta = false },
                onSelect = { update ->
                    manageBeta = false
                    viewModel.selectManagedVersion(update)
                },
            )
        }

        activeUpdate?.let { update ->
            if (settings.onboardingComplete) {
                UpdateDetailsSheet(
                    update = update,
                    viewModel = viewModel,
                    onUpdateLater = {
                        activeUpdate = null
                        if (update.isVersionSwitch) viewModel.dismissVersionSwitch()
                        else viewModel.remindAboutUpdateLater(update)
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
    archiveRestoreState: ArchiveRestoreState,
    archiveUpgradeState: ArchiveUpgradeState,
    archiveBackupDeleteState: ArchiveBackupDeleteState,
    navigationRequest: NavigationRequest?,
    onNavigationRequestHandled: (Int) -> Unit,
    onManageBeta: () -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var reportSubmitted by rememberSaveable { mutableStateOf(false) }
    val initialDestination = remember { navigationRequest?.destination ?: settings.lastDestination }
    val initialRequestId = remember { navigationRequest?.id }
    var initialNavigationHandled by rememberSaveable { mutableStateOf(false) }
    var navigationReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialNavigationHandled) {
            if (initialDestination != LastDestination.Today) navController.navigateTo(initialDestination)
            initialNavigationHandled = true
        }
        initialRequestId?.let(onNavigationRequestHandled)
        navigationReady = true
    }
    LaunchedEffect(navigationReady, navigationRequest) {
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
    var compareRange by remember { mutableStateOf(StatisticsPeriod.Week.range(LocalDate.now())) }
    val statisticsViewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.Factory(
            (context.applicationContext as StillApplication).container.usageRepository,
            settings.appCategoryOverrides,
        ),
    )
    LaunchedEffect(settings.appCategoryOverrides) { statisticsViewModel.updateCategories(settings.appCategoryOverrides) }

    val predictiveBackMarginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val forwardNavigationOffsetPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    // Retain the gesture edge for the completion transition without triggering recomposition
    // from inside Navigation's transition callbacks.
    val backAnimationState = remember { BackAnimationState() }
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            backAnimationState.tabNavigation = false
            if (initialState.destination.route in TopLevelRoutes &&
                targetState.destination.route in TopLevelRoutes
            ) EnterTransition.None else forwardDestinationEnter(forwardNavigationOffsetPx)
        },
        exitTransition = {
            if (initialState.destination.route in TopLevelRoutes &&
                targetState.destination.route in TopLevelRoutes
            ) ExitTransition.None else forwardDestinationExit()
        },
        popEnterTransition = {
            if (backAnimationState.tabNavigation) EnterTransition.None
            else backDestinationEnter(backAnimationState.value, predictiveBackMarginPx)
        },
        popExitTransition = {
            if (backAnimationState.tabNavigation) ExitTransition.None
            else backDestinationExit(backAnimationState.value, predictiveBackMarginPx)
        },
        predictivePopEnterTransition = { swipeEdge ->
            backAnimationState.tabNavigation = false
            backDestinationEnter(swipeEdge, predictiveBackMarginPx)
        },
        predictivePopExitTransition = { swipeEdge ->
            backAnimationState.tabNavigation = false
            backAnimationState.value = swipeEdge
            backDestinationExit(swipeEdge, predictiveBackMarginPx)
        },
    ) {
        composable(TodayRoute) {
            DestinationScaffold(
                topBar = { TodayTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(TodayRoute, navController) {
                    backAnimationState.tabNavigation = true
                } },
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
        composable(TimelineRoute) {
            DestinationScaffold(
                topBar = { TimelineTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(TimelineRoute, navController) {
                    backAnimationState.tabNavigation = true
                } },
            ) { padding ->
                TimelineScreen(
                    selectedDay,
                    availableDays.map { it.date },
                    selectDate,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(AppsRoute) {
            DestinationScaffold(
                topBar = { AppsTopBar { navController.navigate(SettingsRoute) } },
                bottomBar = { StillNavigationBar(AppsRoute, navController) {
                    backAnimationState.tabNavigation = true
                } },
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
        composable(StatisticsRoute) {
            DestinationScaffold(
                topBar = { StatisticsTopBar {
                    compareRange = statisticsViewModel.range.value
                    navController.navigate(CompareRoute)
                } },
                bottomBar = { StillNavigationBar(StatisticsRoute, navController) {
                    backAnimationState.tabNavigation = true
                } },
            ) { padding ->
                StatisticsScreen(statisticsViewModel, availableDays.map { it.date }, modifier = Modifier.padding(padding))
            }
        }
        composable(CompareRoute) {
            val compareViewModel: CompareViewModel = viewModel(
                factory = CompareViewModel.Factory(
                    (context.applicationContext as StillApplication).container.usageRepository,
                    settings.appCategoryOverrides,
                    compareRange,
                ),
            )
            DestinationScaffold(
                topBar = { CompareTopBar {
                    if (!compareViewModel.back() && !navController.popBackStack()) navController.navigate(StatisticsRoute)
                } },
            ) { padding ->
                val appContext = context.applicationContext
                val localApps by produceState<Map<String, AppInfo>>(emptyMap(), appContext, availableDays) {
                    // Launcher queries and label loading must not block the first Compare frame.
                    value = withContext(Dispatchers.IO) {
                        val installed = runCatching {
                            appContext.getSystemService(LauncherApps::class.java)
                                ?.getActivityList(null, Process.myUserHandle()).orEmpty()
                                .map { AppInfo(it.applicationInfo.packageName, it.label.toString()) }
                        }.getOrDefault(emptyList())
                        (installed + availableDays.flatMap { it.apps.orEmpty() }.map { it.app })
                            .groupBy { it.label }.mapNotNull { (label, matches) ->
                                matches.distinctBy { it.packageName }.singleOrNull()?.let { label to it }
                            }.toMap()
                    }
                }
                CompareScreen(compareViewModel, availableDays.map { it.date }, localApps, Modifier.padding(padding))
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
            LaunchedEffect(Unit) { viewModel.refreshStoredDataSummary() }
            LaunchedEffect(storedDataSummary?.importRollbackUntilMillis) {
                storedDataSummary?.importRollbackUntilMillis?.let { until ->
                    kotlinx.coroutines.delay((until - System.currentTimeMillis()).coerceAtLeast(0L))
                    viewModel.refreshStoredDataSummary()
                }
            }
            DestinationScaffold(
                topBar = {
                    SettingsTopBar {
                        if (!navController.popBackStack()) navController.navigate(TodayRoute)
                    }
                },
            ) { padding ->
                SettingsScreen(
                    settings = settings,
                    onThemeClick = { navController.navigate(ThemeSettingsRoute) },
                    onVersionArtworkClick = {
                        navController.navigate(VersionArtworkRoute) { launchSingleTop = true }
                    },
                    onRefresh = viewModel::refresh,
                    onWidgetClick = { navController.navigate(WidgetSettingsRoute) },
                    onStoredDataClick = { navController.navigate(StoredDataRoute) },
                    onReportIssueClick = { navController.navigate(IssueReportRoute) },
                    reportSubmitted = reportSubmitted,
                    onDismissReportSubmitted = { reportSubmitted = false },
                    onDeveloperClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://matepazy.hu")))
                    },
                    onSaveUsageHistoryChange = viewModel::setSaveUsageHistory,
                    storedDataSummary = storedDataSummary,
                    archiveRestoreState = archiveRestoreState,
                    archiveUpgradeState = archiveUpgradeState,
                    onRestoreArchive = viewModel::restoreArchiveBackup,
                    onUpgradeArchive = viewModel::upgradeArchive,
                    onDismissArchiveRestoreResult = viewModel::dismissArchiveRestoreResult,
                    onDismissArchiveUpgradeResult = viewModel::dismissArchiveUpgradeResult,
                    updateState = updateState,
                    onVersionCheckChange = viewModel::setVersionCheckEnabled,
                    onUpdateChannelChange = viewModel::setUpdateChannel,
                    onCheckForUpdates = viewModel::triggerVersionCheck,
                    onManageBeta = onManageBeta,
                    dataTransferState = viewModel.dataTransferState.collectAsStateWithLifecycle().value,
                    onPrepareExport = viewModel::prepareDataExport,
                    onExportDestination = viewModel::exportData,
                    onImportData = viewModel::importData,
                    onSelectDataImport = viewModel::selectDataImport,
                    onRollbackDataImport = viewModel::rollbackDataImport,
                    onDismissDataTransfer = viewModel::dismissDataTransferResult,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(VersionArtworkRoute) {
            VersionArtworkScreen(onClose = { navController.popBackStack() })
        }
        composable(IssueReportRoute) {
            DestinationScaffold(
                topBar = {
                    SettingsTopBar(title = "Report an issue", onBack = { navController.popBackStack() })
                },
            ) { padding ->
                IssueReportBrowser(
                    onSubmitted = {
                        reportSubmitted = true
                        navController.popBackStack()
                    },
                    onClose = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(ThemeSettingsRoute) {
            DestinationScaffold(
                topBar = { SettingsTopBar(title = "Theme", onBack = { navController.popBackStack() }) },
            ) { padding ->
                ThemeSettingsScreen(
                    selectedTheme = settings.theme,
                    promotedSimpleTheme = settings.promotedSimpleTheme,
                    onThemeChange = viewModel::setTheme,
                    onDrawerThemeChange = viewModel::setThemeFromDrawer,
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
                    archiveBackupDeleteState = archiveBackupDeleteState,
                    onDeleteArchiveBackup = viewModel::deleteArchiveBackup,
                    onDismissArchiveBackupDeleteResult = viewModel::dismissArchiveBackupDeleteResult,
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
                        onBack = navController::backFromWidgetSettings,
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
                    onShowThemeGraphicsChange = viewModel::setWidgetShowThemeGraphics,
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
                        onBack = navController::backFromWidgetSettings,
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
                    onShowThemeGraphicsChange = viewModel::setDaylineWidgetShowThemeGraphics,
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
            if (currentDestination?.route != TodayRoute && !popBackStack(TodayRoute, inclusive = false)) {
                navigate(TodayRoute) {
                    popUpTo(graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        LastDestination.Timeline, LastDestination.Apps, LastDestination.Statistics ->
            navigateTopLevel(destination.route)
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

private fun NavHostController.backFromWidgetSettings() {
    if (popBackStack()) return
    val strandedDestination = currentDestination?.id ?: return
    navigate(TodayRoute) {
        popUpTo(strandedDestination) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    if (route == TodayRoute && popBackStack(TodayRoute, inclusive = false)) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = route != TodayRoute
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
private fun StillNavigationBar(
    currentRoute: String,
    navController: NavHostController,
    onTabNavigation: () -> Unit,
) {
    NavigationBar(
        modifier = Modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple(TodayRoute, "Today", StillIcons.Today),
            Triple(TimelineRoute, "Timeline", StillIcons.Timeline),
            Triple(AppsRoute, "Apps", StillIcons.Apps),
            Triple(StatisticsRoute, "Statistics", StillIcons.Statistics),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    onTabNavigation()
                    navController.navigateTopLevel(route)
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
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = StillSpacing.large)
            .clearAndSetSemantics { contentDescription = "Loading screen time" },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(StillSpacing.large))
            LoadingSkeleton(Modifier.width(116.dp).height(30.dp))
            Spacer(Modifier.height(StillSpacing.section))
            LoadingSkeleton(Modifier.width(150.dp).height(20.dp))
            Spacer(Modifier.height(StillSpacing.medium))
            LoadingSkeleton(Modifier.width(184.dp).height(52.dp))
            Spacer(Modifier.height(StillSpacing.large))
            LoadingSkeleton(Modifier.fillMaxWidth().height(188.dp))
            Spacer(Modifier.height(StillSpacing.section))
            LoadingSkeleton(Modifier.width(132.dp).height(26.dp))
            Spacer(Modifier.height(StillSpacing.medium))
            repeat(3) {
                LoadingSkeleton(Modifier.fillMaxWidth().height(52.dp))
                Spacer(Modifier.height(StillSpacing.small))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = StillSpacing.large), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { LoadingSkeleton(Modifier.width(54.dp).height(32.dp)) }
        }
    }
}

@Composable
internal fun PermissionRequiredScreen(
    hasUsageAccess: () -> Boolean,
    usageSettingsIntent: () -> android.content.Intent,
    appInfoIntent: () -> android.content.Intent,
    installedFromApk: () -> Boolean,
    onGranted: () -> Unit,
) {
    UsageAccessFlow(hasUsageAccess, usageSettingsIntent, appInfoIntent, installedFromApk, onGranted) { openUsageSettings ->
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
        Text("Allow screen-time access", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = StillSpacing.large))
        Text(
            "Still needs Android's Usage Access permission to calculate how long you use each app. Your usage information is processed and stored on this device.",
            modifier = Modifier.padding(top = StillSpacing.medium, bottom = StillSpacing.large),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = openUsageSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(25.dp),
        ) { Text("Allow screen-time access") }
    }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(StillSpacing.large), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text("Usage data unavailable", style = MaterialTheme.typography.headlineSmall)
        Text(message, modifier = Modifier.padding(vertical = StillSpacing.medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
