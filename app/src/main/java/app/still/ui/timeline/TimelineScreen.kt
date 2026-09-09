package app.still.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageSession
import app.still.ui.components.AppIcon
import app.still.ui.components.TonalPanel
import app.still.ui.components.clockTime
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopBar(onSettings: () -> Unit) {
    TopAppBar(
        title = { Text("Timeline", style = MaterialTheme.typography.titleLarge) },
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
    )
}

@Composable
fun TimelineScreen(today: DailyUsage, modifier: Modifier = Modifier) {
    if (today.sessions.isEmpty()) {
        Column(modifier.fillMaxSize().padding(StillSpacing.large), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No sessions yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(StillSpacing.small))
            Text("Your first phone-use session today will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = StillSpacing.medium, end = StillSpacing.medium, bottom = StillSpacing.large),
    ) {
        item {
            Text(
                today.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                modifier = Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        itemsIndexed(today.sessions, key = { _, session -> session.start.toEpochMilli() }) { index, session ->
            SessionRow(session, isLast = index == today.sessions.lastIndex)
        }
    }
}

@Composable
private fun SessionRow(session: UsageSession, isLast: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val rail = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(22.dp).fillMaxHeight()) {
            val x = size.width / 2
            if (!isLast) drawLine(rail, Offset(x, 9.dp.toPx()), Offset(x, size.height + 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, 9.dp.toPx()))
        }
        TonalPanel(
            modifier = Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium).clickable { expanded = !expanded },
            contentPadding = PaddingValues(12.dp),
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text("${session.start.clockTime()} – ${session.end.clockTime()}", style = MaterialTheme.typography.titleSmall)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(session.duration.compactDuration(), style = MaterialTheme.typography.titleSmall)
                        Text(if (expanded) "less" else "details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(StillSpacing.small))
                session.apps.take(if (expanded) Int.MAX_VALUE else 3).forEach { usage ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                    ) {
                        AppIcon(usage.app.packageName, usage.app.label, size = 26.dp)
                        Text(usage.app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(usage.duration.compactDuration(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(expanded && session.sequence.size > 1) {
                    Column(Modifier.padding(top = StillSpacing.small)) {
                        Text("App sequence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(session.sequence.joinToString("  →  ") { it.label }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
