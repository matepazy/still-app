package app.still.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DaySelector(
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val latestDate = availableDates.maxOrNull()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, MMM d", locale) }
    val orderedDates = remember(availableDates) { availableDates.distinct().sortedDescending() }

    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                if (selectedDate == latestDate) "Today" else selectedDate.format(formatter),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose day")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            orderedDates.forEach { date ->
                DropdownMenuItem(
                    text = {
                        Text(if (date == latestDate) "Today · ${date.format(formatter)}" else date.format(formatter))
                    },
                    onClick = {
                        expanded = false
                        onDateSelected(date)
                    },
                )
            }
        }
    }
}
