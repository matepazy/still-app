package app.still.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun chartLabelWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val width = labels.maxOfOrNull { measurer.measure(it, style, softWrap = false).size.width } ?: 0
    return with(LocalDensity.current) { width.toDp() } + 8.dp
}

/** Place full labels at their data coordinates, skipping ticks that would overlap. */
@Composable
internal fun ChartTickLabels(ticks: List<Pair<Float, String>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    BoxWithConstraints(modifier.fillMaxWidth()) {
        var previousEnd = (-4).dp
        ticks.forEach { (fraction, label) ->
            val width = with(density) { measurer.measure(label, style, softWrap = false).size.width.toDp() }
                .coerceAtMost(maxWidth)
            val left = (maxWidth * fraction - width / 2).coerceIn(0.dp, (maxWidth - width).coerceAtLeast(0.dp))
            if (left >= previousEnd + 4.dp) {
                Text(label, Modifier.offset(x = left).width(width), style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 1, softWrap = false)
                previousEnd = left + width
            }
        }
    }
}
