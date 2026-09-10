package app.still.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import app.still.domain.model.DaylineKind
import app.still.domain.model.DaylineSegment
import java.time.Duration
import java.time.Instant

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
    val accent = MaterialTheme.colorScheme.tertiary
    val elapsed = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .30f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val off = MaterialTheme.colorScheme.surfaceContainerLow
    val now = MaterialTheme.colorScheme.onSurface
    val reveal by animateFloatAsState(if (segments.isEmpty()) 0f else 1f, label = "Dayline reveal")
    val fullDayMillis = Duration.ofHours(24).toMillis().toFloat()
    val nowProgress = (Duration.between(start, end).toMillis() / fullDayMillis).coerceIn(0f, 1f)

    Column(
        modifier
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = summary
                role = Role.Button
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(86.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val bandTop = 36.dp.toPx()
                val bandHeight = 31.dp.toPx()
                val nowX = size.width * nowProgress
                val outline = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, bandTop, size.width, bandTop + bandHeight, CornerRadius(5.dp.toPx())))
                }
                clipPath(outline) {
                    drawRect(track, Offset(0f, bandTop), Size(size.width, bandHeight))
                    drawRect(elapsed, Offset(0f, bandTop), Size(nowX, bandHeight))
                    var activeIndex = 0
                    segments.forEach { segment ->
                        val leftProgress = (Duration.between(start, segment.start).toMillis() / fullDayMillis).coerceIn(0f, 1f)
                        val rightProgress = (Duration.between(start, segment.end).toMillis() / fullDayMillis).coerceIn(leftProgress, 1f)
                        val left = size.width * leftProgress
                        val width = size.width * (rightProgress - leftProgress) * reveal
                        val color = when (segment.kind) {
                            DaylineKind.Active -> if (activeIndex++ % 3 == 1) accent else active
                            DaylineKind.ScreenOff -> off
                            DaylineKind.Idle -> elapsed
                        }
                        if (width > 0f) drawRect(color, Offset(left, bandTop), Size(width, bandHeight))
                    }
                }
                drawLine(now, Offset(nowX, 17.dp.toPx()), Offset(nowX, 75.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
            }
            val markerOffset = (maxWidth * nowProgress - 13.dp).coerceIn(0.dp, maxWidth - 30.dp)
            Text(
                "Now",
                modifier = Modifier.offset(x = markerOffset).width(30.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = if (index == 4) Modifier else Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
