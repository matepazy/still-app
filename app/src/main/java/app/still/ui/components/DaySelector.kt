package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.still.ui.theme.StillSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun DaySelector(
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val dates = remember(availableDates) { availableDates.distinct().sorted() }
    val latestDate = dates.lastOrNull()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, MMM d", locale) }

    TextButton(
        onClick = { pickerVisible = true },
        enabled = dates.isNotEmpty(),
        modifier = modifier,
    ) {
        Text(
            if (selectedDate == latestDate) "Today" else selectedDate.format(formatter),
            style = MaterialTheme.typography.titleSmall,
        )
        Icon(painterResource(StillIcons.Calendar), contentDescription = "Choose day from calendar")
    }

    if (pickerVisible) {
        CompactDatePickerDialog(
            selectedDate = selectedDate,
            availableDates = dates,
            locale = locale,
            onDismiss = { pickerVisible = false },
            onConfirm = { date ->
                onDateSelected(date)
                pickerVisible = false
            },
        )
    }
}

@Composable
private fun CompactDatePickerDialog(
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    locale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val availableEpochDays = remember(availableDates) { availableDates.mapTo(hashSetOf(), LocalDate::toEpochDay) }
    val months = remember(availableDates) { availableDates.map(YearMonth::from).distinct() }
    val initialMonthIndex = months.indexOf(YearMonth.from(selectedDate)).coerceAtLeast(0)
    var visibleMonthIndex by remember(selectedDate, months) { mutableIntStateOf(initialMonthIndex) }
    var pendingDate by remember(selectedDate) { mutableStateOf(selectedDate) }
    val visibleMonth = months[visibleMonthIndex]
    val fullDateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { List(7) { firstDayOfWeek.plus(it.toLong()) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StillSpacing.large)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(StillSpacing.large)) {
                Text("Choose a date", style = MaterialTheme.typography.headlineSmall)
                Text(
                    pendingDate.format(fullDateFormatter),
                    modifier = Modifier.padding(top = StillSpacing.xSmall),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                MonthNavigation(
                    month = visibleMonth,
                    formatter = monthFormatter,
                    canGoBack = visibleMonthIndex > 0,
                    canGoForward = visibleMonthIndex < months.lastIndex,
                    onBack = { visibleMonthIndex-- },
                    onForward = { visibleMonthIndex++ },
                    modifier = Modifier.padding(top = StillSpacing.large),
                )

                WeekdayHeader(weekDays, locale, Modifier.padding(top = StillSpacing.small))
                MonthGrid(
                    month = visibleMonth,
                    firstDayOfWeek = firstDayOfWeek,
                    availableEpochDays = availableEpochDays,
                    selectedDate = pendingDate,
                    onDateSelected = { pendingDate = it },
                    modifier = Modifier.padding(top = StillSpacing.xSmall),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = StillSpacing.medium),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm(pendingDate) },
                        modifier = Modifier.padding(start = StillSpacing.small),
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigation(
    month: YearMonth,
    formatter: DateTimeFormatter,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = StillSpacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(painterResource(StillIcons.Back), contentDescription = "Previous month")
            }
            Text(
                month.format(formatter),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(painterResource(StillIcons.ChevronRight), contentDescription = "Next month")
            }
        }
    }
}

@Composable
private fun WeekdayHeader(
    days: List<DayOfWeek>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        days.forEach { day ->
            Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    availableEpochDays: Set<Long>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leadingEmptyCells = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cellCount = ((leadingEmptyCells + month.lengthOfMonth() + 6) / 7) * 7

    Column(modifier.fillMaxWidth()) {
        repeat(cellCount / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val dayNumber = week * 7 + weekday - leadingEmptyCells + 1
                    val date = dayNumber.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date == selectedDate,
                                enabled = date.toEpochDay() in availableEpochDays,
                                onClick = { onDateSelected(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
