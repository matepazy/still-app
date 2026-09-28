package app.still

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.still.data.settings.LastDestination
import app.still.ui.MainViewModel
import app.still.ui.NavigationRequest
import app.still.ui.StillApp

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)
    private var navigationRequestId = 0

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as StillApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) navigationRequest = intent.consumeWidgetNavigationRequest()
        enableEdgeToEdge()
        setContent {
            StillApp(viewModel, navigationRequest) { handledId ->
                if (navigationRequest?.id == handledId) navigationRequest = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.consumeWidgetNavigationRequest()?.let { navigationRequest = it }
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun Intent.consumeWidgetNavigationRequest(): NavigationRequest? {
        val destination = when {
            getBooleanExtra(EXTRA_OPEN_SCREEN_TIME_WIDGET_SETTINGS, false) -> LastDestination.ScreenTimeWidgetSettings
            getBooleanExtra(EXTRA_OPEN_DAYLINE_WIDGET_SETTINGS, false) -> LastDestination.DaylineWidgetSettings
            getBooleanExtra(EXTRA_OPEN_WIDGET_SETTINGS, false) -> LastDestination.WidgetSettings
            getBooleanExtra(EXTRA_OPEN_TODAY, false) -> LastDestination.Today
            else -> return null
        }
        removeExtra(EXTRA_OPEN_SCREEN_TIME_WIDGET_SETTINGS)
        removeExtra(EXTRA_OPEN_DAYLINE_WIDGET_SETTINGS)
        removeExtra(EXTRA_OPEN_WIDGET_SETTINGS)
        removeExtra(EXTRA_OPEN_TODAY)
        return NavigationRequest(destination, ++navigationRequestId)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "app.still.extra.OPEN_TODAY"
        const val EXTRA_OPEN_WIDGET_SETTINGS = "app.still.extra.OPEN_WIDGET_SETTINGS"
        const val EXTRA_OPEN_SCREEN_TIME_WIDGET_SETTINGS = "app.still.extra.OPEN_SCREEN_TIME_WIDGET_SETTINGS"
        const val EXTRA_OPEN_DAYLINE_WIDGET_SETTINGS = "app.still.extra.OPEN_DAYLINE_WIDGET_SETTINGS"
    }
}
