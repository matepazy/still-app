package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Wrap complete choices into rows so every option remains visible at larger text sizes. */
@Composable
fun AdaptiveChoiceRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val itemWidths = labels.map { label ->
        with(density) { measurer.measure(label, style, softWrap = false, maxLines = 1).size.width.toDp() + 24.dp }
            .coerceAtLeast(48.dp)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rows = mutableListOf<MutableList<Int>>()
        var occupiedWidth = 0.dp
        labels.indices.forEach { index ->
            if (rows.isEmpty() || occupiedWidth + itemWidths[index] > maxWidth) {
                rows.add(mutableListOf())
                occupiedWidth = 0.dp
            }
            rows.last().add(index)
            occupiedWidth += itemWidths[index]
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { indices ->
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    indices.forEachIndexed { position, index ->
                        SegmentedButton(
                            modifier = Modifier.weight(itemWidths[index].value),
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            shape = SegmentedButtonDefaults.itemShape(position, indices.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                activeBorderColor = MaterialTheme.colorScheme.outline,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            icon = {},
                            label = { Text(labels[index], style = style) },
                        )
                    }
                }
            }
        }
    }
}
