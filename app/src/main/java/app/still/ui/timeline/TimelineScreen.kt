package app.still.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageSession
import app.still.ui.components.AdaptiveValueRow
import app.still.ui.components.AppIcon
import app.still.ui.components.DaySelector
import app.still.ui.components.StillIcons
import app.still.ui.components.TonalPanel
import app.still.ui.components.clockTime
import app.still.ui.components.compactDuration
import app.still.ui.theme.StillSpacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopBar(onSettings: () -> Unit) {
    TopAppBar(colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
    ),
        title = { Text("Timeline", style = MaterialTheme.typography.titleLarge) },
        actions = { IconButton(onClick = onSettings) { Icon(painterResource(StillIcons.Settings), contentDescription = "Settings") } },
    )
}

@Composable
fun TimelineScreen(
    day: DailyUsage,
    availableDates: List<LocalDate> = listOf(day.date),
    onDateSelected: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (day.sessions.isEmpty()) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(StillSpacing.large), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DaySelector(day.date, availableDates, onDateSelected)
            Spacer(Modifier.height(StillSpacing.large))
            Text(
                if (day.detailsAvailable) "No sessions recorded" else "Daily total only",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(StillSpacing.small))
            Text(
                if (day.detailsAvailable) {
                    "Choose another day or check back after Android records foreground use."
                } else {
                    "Android had already removed this day’s session timeline, but Still recovered its app totals."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val sessions = day.sessions.sortedByDescending { it.start }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = StillSpacing.medium, end = StillSpacing.medium, bottom = StillSpacing.large),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = StillSpacing.medium), horizontalArrangement = Arrangement.Center) {
                DaySelector(day.date, availableDates, onDateSelected)
            }
        }
        itemsIndexed(sessions, key = { _, session -> session.start.toEpochMilli() }) { index, session ->
            SessionRow(session, isLast = index == sessions.lastIndex)
        }
    }
}

@Composable
private fun SessionRow(session: UsageSession, isLast: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val rail = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().drawBehind {
            val x = 11.dp.toPx()
            if (!isLast) drawLine(rail, Offset(x, 9.dp.toPx()), Offset(x, size.height + 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, 9.dp.toPx()))
        }) {
        TonalPanel(
            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, bottom = StillSpacing.medium).clickable { expanded = !expanded },
            contentPadding = PaddingValues(12.dp),
        ) {
            Column {
                AdaptiveValueRow(
                    minLeadingWidth = 120.dp,
                    leading = { Text("${session.start.clockTime()} – ${session.end.clockTime()}", style = MaterialTheme.typography.titleSmall) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(session.activeDuration.compactDuration(), style = MaterialTheme.typography.titleSmall)
                            Text(if (expanded) "less" else "details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                Spacer(Modifier.height(StillSpacing.small))
                session.apps.take(if (expanded) Int.MAX_VALUE else 3).forEach { usage ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StillSpacing.small),
                    ) {
                        AppIcon(usage.app.packageName, usage.app.label, size = 26.dp)
                        AdaptiveValueRow(
                            modifier = Modifier.weight(1f),
                            minLeadingWidth = 72.dp,
                            leading = { Text(usage.app.label, style = MaterialTheme.typography.bodyMedium) },
                            trailing = { Text(usage.duration.compactDuration(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                }
            }
        }
    }
}
