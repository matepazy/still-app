package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.still.domain.model.StatisticsRange
import app.still.ui.theme.StillSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch

@Composable
fun CompactDateRangePickerDialog(
    selectedRange: StatisticsRange,
    availableDates: List<LocalDate>,
    onDismiss: () -> Unit,
    onConfirm: (StatisticsRange) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val today = LocalDate.now()
    val earliestDate = maxOf(
        today.minusYears(10),
        minOf(availableDates.minOrNull() ?: today, selectedRange.start),
    )
    val months = remember(earliestDate, today) {
        val first = YearMonth.from(earliestDate)
        val last = YearMonth.from(today)
        (0..ChronoUnit.MONTHS.between(first, last).toInt()).map { first.plusMonths(it.toLong()) }
    }
    val availableEpochDays = remember(availableDates, earliestDate, today) {
        availableDates.asSequence()
            .filter { !it.isBefore(earliestDate) && !it.isAfter(today) }
            .mapTo(hashSetOf(), LocalDate::toEpochDay)
    }
    val pagerState = rememberPagerState(
        initialPage = months.indexOf(YearMonth.from(selectedRange.endInclusive)).coerceIn(0, months.lastIndex),
        pageCount = { months.size },
    )
    val scope = rememberCoroutineScope()
    var pendingStart by remember(selectedRange) { mutableStateOf(selectedRange.start) }
    var pendingEnd by remember(selectedRange) { mutableStateOf<LocalDate?>(selectedRange.endInclusive) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d, yyyy", locale) }
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { List(7) { firstDayOfWeek.plus(it.toLong()) } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = StillSpacing.large).widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(StillSpacing.large)) {
                Text("Choose a range", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${pendingStart.format(dateFormatter)} – ${pendingEnd?.format(dateFormatter) ?: "Select end date"}",
                    modifier = Modifier.padding(top = StillSpacing.xSmall),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MonthNavigation(
                    month = months[pagerState.currentPage],
                    formatter = monthFormatter,
                    canGoBack = pagerState.currentPage > 0,
                    canGoForward = pagerState.currentPage < months.lastIndex,
                    onBack = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onForward = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.padding(top = StillSpacing.large),
                )
                WeekdayHeader(weekDays, locale, Modifier.padding(top = StillSpacing.small))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(top = StillSpacing.xSmall).height(264.dp),
                ) { page ->
                    MonthGrid(
                        month = months[page],
                        firstDayOfWeek = firstDayOfWeek,
                        availableEpochDays = availableEpochDays,
                        selectedDate = pendingStart,
                        selectedEndDate = pendingEnd,
                        onDateSelected = { date ->
                            when {
                                pendingEnd != null -> { pendingStart = date; pendingEnd = null }
                                date.isBefore(pendingStart) -> pendingStart = date
                                else -> pendingEnd = date
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = StillSpacing.medium),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { pendingEnd?.let { onConfirm(StatisticsRange(pendingStart, it)) } },
                        enabled = pendingStart.toEpochDay() in availableEpochDays &&
                            pendingEnd?.toEpochDay()?.let(availableEpochDays::contains) == true,
                        modifier = Modifier.padding(start = StillSpacing.small),
                    ) { Text("Apply") }
                }
            }
        }
    }
}
