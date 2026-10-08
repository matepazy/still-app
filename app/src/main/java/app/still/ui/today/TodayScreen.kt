package app.still.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.still.ui.components.DurationHeadline
import app.still.ui.components.AdaptivePair
import app.still.domain.model.DailyUsage
import app.still.domain.model.UsageDashboard
import app.still.data.settings.ThemePreference
import app.still.ui.components.AppIcon
import app.still.ui.components.StillIcons
import app.still.ui.components.Dayline
import app.still.ui.components.StillWordmark
import app.still.ui.components.TonalPanel
import app.still.ui.components.clockTime
import app.still.ui.components.compactDuration
import app.still.ui.components.signedCompactDuration
import app.still.ui.theme.StillSpacing
import app.still.ui.theme.LocalThemePreference
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTopBar(onSettings: () -> Unit) {
    val community = app.still.ui.theme.LocalCommunityStyle.current
    val colors = community?.let { app.still.ui.theme.communityControlColors(it.colors) } ?: MaterialTheme.colorScheme
    TopAppBar(colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
    ),
        title = { StillWordmark() },
        actions = {
            IconButton(onClick = onSettings, colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                containerColor = if (community != null) colors.surfaceContainer else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (community != null) colors.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )) {
                Icon(painterResource(StillIcons.Settings), contentDescription = "Settings")
            }
        },
    )
}

@Composable
fun TodayScreen(
    dashboard: UsageDashboard,
    onDaylineClick: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = dashboard.today
    val communityArtwork = app.still.ui.theme.LocalCommunityStyle.current?.image("today.background")
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StillSpacing.medium),
    ) {
        Spacer(Modifier.height(24.dp))
        communityArtwork?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.height(StillSpacing.medium))
        }
        val date = today.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        if (LocalThemePreference.current == ThemePreference.Halloween) {
            HalloweenTodayHeader(date, today.total.compactDuration())
        } else if (LocalThemePreference.current == ThemePreference.Fall) {
            FallTodayHeader(date, today.total.compactDuration())
        } else {
            Text(
                "Today · $date",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(StillSpacing.medium))
            DurationHeadline(today.total.compactDuration())
            Text("Screen time today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(StillSpacing.large))
        Comparison(dashboard)

        Spacer(Modifier.height(40.dp))
        Text("Dayline", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(StillSpacing.small))
        Dayline(
            start = today.rangeStart,
            end = today.rangeEnd,
            segments = today.dayline,
            summary = "Dayline showing ${today.sessions.size} usage sessions and ${today.total.compactDuration()} of active use",
            onClick = onDaylineClick,
        )

        Spacer(Modifier.height(40.dp))
        AdaptivePair { itemModifier ->
            MetricPanel(
                value = "${today.checkInCount}",
                label = "check-ins",
                detail = quickCheckText(today),
                icon = { Icon(painterResource(StillIcons.CheckIn), contentDescription = null) },
                modifier = itemModifier,
            )
            MetricPanel(
                value = today.longestBreak?.compactDuration() ?: "—",
                label = "longest break",
                detail = if (today.longestBreak == null) "After first use" else "Today",
                icon = { Icon(painterResource(StillIcons.Break), contentDescription = null) },
                modifier = itemModifier,
            )
        }

        Spacer(Modifier.height(40.dp))
        Text("Most changed", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(StillSpacing.small))
        val changed = dashboard.mostChanged
        val changedPanelModifier = if (changed == null) {
            Modifier.fillMaxWidth().heightIn(min = 80.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .clickable { onAppClick(changed.app.packageName) }
        }
        TonalPanel(changedPanelModifier, contentPadding = PaddingValues(12.dp)) {
            if (changed == null) {
                Column {
                    Text("Building your app baseline", style = MaterialTheme.typography.titleMedium)
                    Text("A comparison appears after a few days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(changed.app.packageName, changed.app.label, size = 38.dp)
                    Column(Modifier.weight(1f).padding(horizontal = StillSpacing.medium)) {
                        Text(changed.app.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(changed.difference.signedCompactDuration(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (changed.difference.isNegative) "Largest decrease today" else "Largest increase today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(StillIcons.ChevronRight), contentDescription = "View app details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(StillSpacing.large))
    }
}

@Composable
private fun Comparison(dashboard: UsageDashboard) {
    val comparison = dashboard.comparison
    if (comparison == null) {
        Text("Building your baseline", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val less = comparison.difference.isNegative
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
        Icon(
            painterResource(if (less) StillIcons.ArrowDown else StillIcons.ArrowUp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${comparison.difference.abs().compactDuration()} ${if (less) "less" else "more"} than your usual\nby ${dashboard.today.rangeEnd.clockTime()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricPanel(
    value: String,
    label: String,
    detail: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalPanel(modifier.heightIn(min = 100.dp), contentPadding = PaddingValues(12.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StillSpacing.small)) {
                icon()
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(StillSpacing.xSmall))
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun quickCheckText(today: DailyUsage): String {
    val quick = today.quickCheckCount
    return when (quick) {
        0 -> "None less than a minute"
        1 -> "1 less than a minute"
        else -> "$quick less than a minute"
    }
}
