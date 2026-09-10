package app.still.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.still.domain.model.AppUsage
import app.still.domain.model.DailyUsage
import app.still.ui.components.AppIcon
import app.still.ui.components.StillIcons
import app.still.ui.components.DaySelector
import app.still.ui.components.TonalPanel
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTopBar(onSettings: () -> Unit) {
    TopAppBar(
        title = { Text("Apps", style = MaterialTheme.typography.titleLarge) },
        actions = { IconButton(onClick = onSettings) { Icon(painterResource(StillIcons.Settings), contentDescription = "Settings") } },
    )
}

@Composable
fun AppsScreen(
    day: DailyUsage,
    onAppClick: (String) -> Unit,
    availableDates: List<LocalDate> = listOf(day.date),
    onDateSelected: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (day.apps.isEmpty()) {
        Column(modifier.fillMaxSize().padding(StillSpacing.large), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DaySelector(day.date, availableDates, onDateSelected)
            Spacer(Modifier.height(StillSpacing.large))
            Text("No app usage recorded", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(StillSpacing.small))
            Text("Apps will appear after Android records foreground use.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = StillSpacing.medium, vertical = StillSpacing.small),
        verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = StillSpacing.small), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    DaySelector(day.date, availableDates, onDateSelected)
                    Text("Ordered by usage time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(day.total.compactDuration(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(day.apps, key = { it.app.packageName }) { usage ->
            AppUsageRow(usage, day.total.toMillis(), onAppClick)
        }
    }
}

@Composable
private fun AppUsageRow(usage: AppUsage, totalMillis: Long, onAppClick: (String) -> Unit) {
    TonalPanel(
        Modifier.fillMaxWidth().clickable { onAppClick(usage.app.packageName) },
        contentPadding = PaddingValues(10.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(usage.app.packageName, usage.app.label, size = 38.dp)
                Spacer(Modifier.width(StillSpacing.medium))
                Text(usage.app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Column(horizontalAlignment = Alignment.End) {
                    Text(usage.duration.compactDuration(), style = MaterialTheme.typography.titleSmall)
                    Text("${usage.opens} ${if (usage.opens == 1) "open" else "opens"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(StillSpacing.small))
            UsageProgress(if (totalMillis <= 0) 0f else (usage.duration.toMillis().toFloat() / totalMillis).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun UsageProgress(progress: Float) {
    val active = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        val y = size.height / 2
        drawLine(track, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(active, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * progress, y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
