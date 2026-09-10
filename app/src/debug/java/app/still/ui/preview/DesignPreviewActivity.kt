package app.still.ui.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.still.data.settings.ThemePreference
import app.still.data.settings.UserSettings
import app.still.ui.PermissionRequiredScreen
import app.still.ui.UsageUiState
import app.still.ui.appdetail.AppDetailScreen
import app.still.ui.appdetail.AppDetailTopBar
import app.still.ui.apps.AppsScreen
import app.still.ui.apps.AppsTopBar
import app.still.ui.onboarding.OnboardingScreen
import app.still.ui.settings.SettingsScreen
import app.still.ui.settings.SettingsTopBar
import app.still.ui.theme.StillTheme
import app.still.ui.timeline.TimelineScreen
import app.still.ui.timeline.TimelineTopBar
import app.still.ui.today.TodayScreen
import app.still.ui.today.TodayTopBar

class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "today"
        val light = intent.getBooleanExtra("light", false)
        setContent {
            StillTheme(if (light) ThemePreference.Light else ThemePreference.Dark, false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { DesignScreen(screen) }
            }
        }
    }
}

@Composable
private fun DesignScreen(screen: String) {
    when (screen) {
        "onboarding" -> OnboardingScreen(UsageUiState.PermissionRequired, {}, {})
        "permission" -> PermissionRequiredScreen {}
        "detail" -> Scaffold(topBar = { AppDetailTopBar("Instagram", {}) }) { padding ->
            AppDetailScreen(PreviewFixtures.appDetail, Modifier.padding(padding))
        }
        "settings" -> Scaffold(topBar = { SettingsTopBar {} }) { padding ->
            SettingsScreen(UserSettings(true, ThemePreference.Dark, false, null), {}, {}, {}, {}, Modifier.padding(padding))
        }
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
                else -> TodayTopBar {}
            }
        },
        bottomBar = { PreviewNavigationBar(screen) },
    ) { padding ->
        when (screen) {
            "timeline" -> TimelineScreen(PreviewFixtures.today, modifier = Modifier.padding(padding))
            "apps" -> AppsScreen(PreviewFixtures.today, {}, modifier = Modifier.padding(padding))
            else -> TodayScreen(PreviewFixtures.dashboard, {}, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PreviewNavigationBar(selected: String) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        listOf(
            Triple("today", "Today", Icons.Default.Home),
            Triple("timeline", "Timeline", Icons.Default.BarChart),
            Triple("apps", "Apps", Icons.Default.Apps),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = selected == route,
                onClick = {},
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
