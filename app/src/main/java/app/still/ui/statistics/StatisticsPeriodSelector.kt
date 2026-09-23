package app.still.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsPeriodSelector(selected: StatisticsPeriod, onSelect: (StatisticsPeriod) -> Unit, onCustom: (StatisticsRange) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val options = StatisticsPeriod.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { if (period == StatisticsPeriod.Custom) showPicker = true else onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(period.label, maxLines = 1) },
            )
        }
    }
    if (showPicker) {
        val picker = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = picker.selectedStartDateMillis
                    val end = picker.selectedEndDateMillis
                    if (start != null && end != null) {
                        onCustom(StatisticsRange(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate(),
                        ))
                        showPicker = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = picker) }
    }
}
