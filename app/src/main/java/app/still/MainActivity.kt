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
import app.still.widget.ScreenTimeWidgetProvider

class MainActivity : ComponentActivity() {
    private var navigationRequest by mutableStateOf<NavigationRequest?>(null)
    private var navigationRequestId = 0

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as StillApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationRequest = intent.widgetNavigationRequest()
        enableEdgeToEdge()
        setContent {
            StillApp(viewModel, navigationRequest) { handledId ->
                if (navigationRequest?.id == handledId) navigationRequest = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.widgetNavigationRequest()?.let { navigationRequest = it }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        ScreenTimeWidgetProvider.updateAll(applicationContext)
    }

    private fun Intent.widgetNavigationRequest(): NavigationRequest? {
        val destination = when {
            getBooleanExtra(EXTRA_OPEN_WIDGET_SETTINGS, false) -> LastDestination.WidgetSettings
            getBooleanExtra(EXTRA_OPEN_TODAY, false) -> LastDestination.Today
            else -> return null
        }
        return NavigationRequest(destination, ++navigationRequestId)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "app.still.extra.OPEN_TODAY"
        const val EXTRA_OPEN_WIDGET_SETTINGS = "app.still.extra.OPEN_WIDGET_SETTINGS"
    }
}
