package app.still.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import app.still.ui.components.AdaptiveChoiceRow
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
    AdaptiveChoiceRow(
        labels = options.map { it.label },
        selectedIndex = options.indexOf(selected),
        onSelect = { index ->
            val period = options[index]
            if (period == StatisticsPeriod.Custom) showPicker = true else onSelect(period)
        },
    )
    if (showPicker) {
        CompactDateRangePickerSheet(
            selectedRange = selectedRange,
            availableDates = availableDates,
            onDismiss = { showPicker = false },
            onConfirm = { range -> onCustom(range); showPicker = false },
        )
    }
}
