package app.still.ui.statistics

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
import app.still.ui.components.CompactDateRangePickerDialog
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
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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
                label = { Text(period.label, maxLines = 1) },
            )
        }
    }
    if (showPicker) {
        CompactDateRangePickerDialog(
            selectedRange = selectedRange,
            availableDates = availableDates,
            onDismiss = { showPicker = false },
            onConfirm = { range -> onCustom(range); showPicker = false },
        )
    }
}
