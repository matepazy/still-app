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
                drawRoundRect(track, Offset(0f, bandTop), Size(size.width, bandHeight), CornerRadius(5.dp.toPx()))
                drawRoundRect(elapsed, Offset(0f, bandTop), Size(nowX, bandHeight), CornerRadius(5.dp.toPx()))

                segments.forEachIndexed { index, segment ->
                    val leftProgress = (Duration.between(start, segment.start).toMillis() / fullDayMillis).coerceIn(0f, 1f)
                    val rightProgress = (Duration.between(start, segment.end).toMillis() / fullDayMillis).coerceIn(leftProgress, 1f)
                    val left = size.width * leftProgress
                    val rawWidth = size.width * (rightProgress - leftProgress) * reveal
                    when (segment.kind) {
                        DaylineKind.Active -> {
                            val width = rawWidth.coerceAtLeast(2.dp.toPx())
                            drawRoundRect(if (index % 3 == 1) accent else active, Offset(left, 25.dp.toPx()), Size(width, 42.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }
                        DaylineKind.ScreenOff -> if (rawWidth > 0f) {
                            drawRoundRect(off, Offset(left, bandTop), Size(rawWidth, bandHeight), CornerRadius(3.dp.toPx()))
                        }
                        DaylineKind.Idle -> Unit
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
