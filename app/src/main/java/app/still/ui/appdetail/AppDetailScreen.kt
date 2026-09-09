package app.still.ui.appdetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.still.domain.model.AppDetail
import app.still.ui.components.AppIcon
import app.still.ui.components.TonalPanel
import app.still.ui.components.clockTime
import app.still.ui.components.compactDuration
import app.still.ui.components.signedCompactDuration
import app.still.ui.theme.StillSpacing
import java.time.Duration
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailTopBar(@Suppress("UNUSED_PARAMETER") title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        actions = { IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "More options") } },
    )
}

@Composable
fun AppDetailScreen(detail: AppDetail, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = StillSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(detail.usage.app.packageName, detail.usage.app.label, size = 70.dp)
        Spacer(Modifier.height(StillSpacing.small))
        Text(detail.usage.app.label, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(StillSpacing.large))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HeroStat(detail.usage.duration.compactDuration(), "today")
            Spacer(Modifier.width(56.dp))
            HeroStat(detail.usage.opens.toString(), "opens")
        }
        Spacer(Modifier.height(StillSpacing.large))
        SevenDayChart(detail)
        Spacer(Modifier.height(StillSpacing.medium))
        SummaryPanel(detail)
        Spacer(Modifier.height(StillSpacing.large))
        Text("Today’s sessions", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(StillSpacing.small))
        TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Column {
                if (detail.sessions.isEmpty()) {
                    Text("No sessions today", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else detail.sessions.forEach { session ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${session.start.clockTime()} – ${session.end.clockTime()}", style = MaterialTheme.typography.bodyMedium)
                        val appDuration = session.apps.firstOrNull { it.app.packageName == detail.usage.app.packageName }?.duration
                        Text(appDuration?.compactDuration() ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryPanel(detail: AppDetail) {
    val change = detail.averageDaily?.let { detail.usage.duration.minus(it) }
    TonalPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Daily average", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail.averageDaily?.compactDuration() ?: "Building", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("Compared to average", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(change?.signedCompactDuration() ?: "—", style = MaterialTheme.typography.titleMedium, color = if (change?.isNegative == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SevenDayChart(detail: AppDetail) {
    val locale = LocalLocale.current.platformLocale
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val unavailable = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = detail.dailyUsage.mapNotNull { it.duration?.toMillis() }.maxOrNull()?.coerceAtLeast(Duration.ofMinutes(1).toMillis()) ?: 1L
    val description = detail.dailyUsage.joinToString { day ->
        "${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}: ${day.duration?.compactDuration() ?: "unavailable"}"
    }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Seven day usage chart. $description" }) {
        Text("Previous 7 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(StillSpacing.small))
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            val chartTop = 4.dp.toPx()
            val chartBottom = size.height - 4.dp.toPx()
            repeat(3) { index ->
                val y = chartTop + (chartBottom - chartTop) * index / 2f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val gap = 12.dp.toPx()
            val barWidth = (size.width - gap * (detail.dailyUsage.size - 1)) / detail.dailyUsage.size.coerceAtLeast(1)
            detail.dailyUsage.forEachIndexed { index, day ->
                val left = index * (barWidth + gap)
                val height = day.duration?.let { (chartBottom - chartTop) * (it.toMillis().toFloat() / max) } ?: 3.dp.toPx()
                drawRoundRect(
                    color = if (day.duration == null) unavailable else primary.copy(alpha = if (index == detail.dailyUsage.lastIndex) 1f else .45f),
                    topLeft = Offset(left, chartBottom - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = StillSpacing.xSmall), horizontalArrangement = Arrangement.SpaceBetween) {
            detail.dailyUsage.forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
