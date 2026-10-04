package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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

/** Only the oversized hero value adapts its size; body text keeps system scaling. */
@Composable
fun DurationHeadline(value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    BasicText(
        text = value,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displayLarge.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 64.sp, stepSize = 1.sp),
    )
}
