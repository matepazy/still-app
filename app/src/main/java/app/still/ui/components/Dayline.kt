package app.still.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.domain.model.DaylineKind
import app.still.domain.model.DaylineSegment
import app.still.ui.theme.StillSpacing
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DaylineTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun Dayline(
    start: Instant,
    end: Instant,
    segments: List<DaylineSegment>,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outline.copy(alpha = .55f)
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val future = MaterialTheme.colorScheme.surfaceContainerLow
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f)
    val now = MaterialTheme.colorScheme.onSurface
    val reveal by animateFloatAsState(if (segments.isEmpty()) 0f else 1f, label = "Dayline reveal")
    val fullDayMillis = Duration.ofHours(24).toMillis().toFloat()
    val nowProgress = (Duration.between(start, end).toMillis() / fullDayMillis).coerceIn(0f, 1f)
    val nowLabel = "Now ${DaylineTimeFormatter.format(end.atZone(ZoneId.systemDefault()))}"

    Column(
        modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = summary
                role = Role.Button
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(82.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val bandTop = 26.dp.toPx()
                val bandHeight = 42.dp.toPx()
                val nowX = size.width * nowProgress
                val outline = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, bandTop, size.width, bandTop + bandHeight, CornerRadius(6.dp.toPx())))
                }
                clipPath(outline) {
                    drawRect(future, Offset(0f, bandTop), Size(size.width, bandHeight))
                    segments.forEach { segment ->
                        val leftProgress = (Duration.between(start, segment.start).toMillis() / fullDayMillis).coerceIn(0f, 1f)
                        val rightProgress = (Duration.between(start, segment.end).toMillis() / fullDayMillis).coerceIn(leftProgress, 1f)
                        val left = size.width * leftProgress
                        val naturalWidth = size.width * (rightProgress - leftProgress)
                        val width = if (segment.kind == DaylineKind.Active && naturalWidth > 0f) {
                            maxOf(naturalWidth, 1.5.dp.toPx()) * reveal
                        } else {
                            naturalWidth * reveal
                        }
                        val color = when (segment.kind) {
                            DaylineKind.Active -> active
                            DaylineKind.ScreenOff -> off
                            DaylineKind.Idle -> idle
                        }
                        if (width > 0f) drawRect(color, Offset(left, bandTop), Size(width, bandHeight))
                    }
                    listOf(.25f, .5f, .75f).forEach { progress ->
                        val x = size.width * progress
                        drawLine(guide, Offset(x, bandTop), Offset(x, bandTop + bandHeight), 1.dp.toPx())
                    }
                }
                drawLine(now, Offset(nowX, 18.dp.toPx()), Offset(nowX, 75.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(now, radius = 3.dp.toPx(), center = Offset(nowX, bandTop + bandHeight / 2))
            }
            val markerLabelWidth = 72.dp
            val markerOffset = (maxWidth * nowProgress - markerLabelWidth / 2).coerceIn(0.dp, maxWidth - markerLabelWidth)
            Text(
                nowLabel,
                modifier = Modifier.offset(x = markerOffset).width(markerLabelWidth),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val labelWidth = 40.dp
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEachIndexed { index, label ->
                val progress = index / 4f
                val labelOffset = (maxWidth * progress - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(
                    label,
                    modifier = Modifier.offset(x = labelOffset).width(labelWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
        Row(
            Modifier.padding(top = StillSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaylineLegendItem("Screen use", active)
            DaylineLegendItem("Idle", idle)
            DaylineLegendItem("Screen off", off)
        }
    }
}

@Composable
private fun DaylineLegendItem(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
