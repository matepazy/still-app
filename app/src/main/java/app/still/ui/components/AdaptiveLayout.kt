package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Give each item a full row when display zoom or larger fonts crowd a pair. */
@Composable
fun AdaptivePair(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    minItemWidth: Dp = 148.dp,
    content: @Composable (Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < minItemWidth * fontScale * 2 + spacing) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                content(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                content(Modifier.weight(1f))
            }
        }
    }
}

/** Label/value rows keep their trailing edge, unlike equal-width metric cards. */
@Composable
fun AdaptiveValueRow(
    modifier: Modifier = Modifier,
    minLeadingWidth: Dp = 148.dp,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { leading() }
            Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        },
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val width = constraints.maxWidth
                val gap = 8.dp.roundToPx()
                // Intrinsic text widths include Android's actual font scaling. Reserve the
                // complete value before deciding whether both pieces fit on one line.
                val leadingWidth = maxOf(minLeadingWidth.roundToPx(), measurables[0].maxIntrinsicWidth(Constraints.Infinity))
                val trailingWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
                val stacked = leadingWidth.toLong() + trailingWidth + gap > width
                val trailingPlaceable = measurables[1].measure(Constraints(maxWidth = width))
                val leadingPlaceable = measurables[0].measure(Constraints(
                    maxWidth = if (stacked) width else (width - trailingPlaceable.width - gap).coerceAtLeast(0),
                ))
                val height = if (stacked) leadingPlaceable.height + gap + trailingPlaceable.height
                    else maxOf(leadingPlaceable.height, trailingPlaceable.height)
                return layout(width, constraints.constrainHeight(height)) {
                    leadingPlaceable.placeRelative(0, if (stacked) 0 else (height - leadingPlaceable.height) / 2)
                    trailingPlaceable.placeRelative(width - trailingPlaceable.width,
                        if (stacked) leadingPlaceable.height + gap else (height - trailingPlaceable.height) / 2)
                }
            }
            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                (measurables[0].maxIntrinsicWidth(height).toLong() + measurables[1].maxIntrinsicWidth(height) + 8.dp.roundToPx())
                    .coerceAtMost(Constraints.Infinity.toLong()).toInt()

            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                maxOf(measurables[0].minIntrinsicWidth(height), measurables[1].minIntrinsicWidth(height))

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
                val gap = 8.dp.roundToPx()
                val trailingWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width)
                val leadingWidth = maxOf(minLeadingWidth.roundToPx(), measurables[0].maxIntrinsicWidth(Constraints.Infinity))
                return if (leadingWidth.toLong() + trailingWidth + gap > width) {
                    measurables[0].maxIntrinsicHeight(width) + gap + measurables[1].maxIntrinsicHeight(width)
                } else {
                    maxOf(measurables[0].maxIntrinsicHeight(minOf(leadingWidth, (width - trailingWidth - gap).coerceAtLeast(0))),
                        measurables[1].maxIntrinsicHeight(trailingWidth))
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                maxIntrinsicHeight(measurables, width)
        },
    )
}

/** Only the oversized hero value adapts its size; body text keeps system scaling. */
@Composable
fun DurationHeadline(value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    BasicText(
        text = value,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displayLarge.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 64.sp, stepSize = 1.sp),
    )
}
