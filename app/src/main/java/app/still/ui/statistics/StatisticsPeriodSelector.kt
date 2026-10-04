package app.still.ui.statistics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import app.still.ui.components.CompactDateRangePickerSheet
import java.time.LocalDate

@Composable
fun StatisticsPeriodSelector(
    selected: StatisticsPeriod,
    selectedRange: StatisticsRange,
    availableDates: List<LocalDate>,
    onSelect: (StatisticsPeriod) -> Unit,
    onCustom: (StatisticsRange) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val options = StatisticsPeriod.entries
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val itemWidth = with(density) {
        options.maxOf { textMeasurer.measure(it.label, labelStyle).size.width }.toDp() + 32.dp
    }.coerceAtLeast(56.dp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rowWidth = maxOf(maxWidth, itemWidth * options.size)
        androidx.compose.foundation.layout.Box(Modifier.horizontalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(Modifier.width(rowWidth)) {
                options.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selected == period,
                        onClick = { if (period == StatisticsPeriod.Custom) showPicker = true else onSelect(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.outline,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        icon = {},
                        label = { Text(period.label, maxLines = 1) },
                    )
                }
            }
        }
    }
    if (showPicker) {
        CompactDateRangePickerSheet(
            selectedRange = selectedRange,
            availableDates = availableDates,
            onDismiss = { showPicker = false },
            onConfirm = { range -> onCustom(range); showPicker = false },
        )
    }
}
