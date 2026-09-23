package app.still.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.data.settings.AppCategory
import app.still.data.usage.UsageRepository
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import app.still.domain.model.StatisticsSummary
import app.still.domain.statistics.StatisticsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface StatisticsState {
    data object Loading : StatisticsState
    data class Ready(val summary: StatisticsSummary) : StatisticsState
    data class Error(val message: String) : StatisticsState
}

class StatisticsViewModel(
    private val repository: UsageRepository,
    private var overrides: Map<String, AppCategory>,
) : ViewModel() {
    private val today = LocalDate.now()
    private val _state = MutableStateFlow<StatisticsState>(StatisticsState.Loading)
    val state: StateFlow<StatisticsState> = _state
    private val _period = MutableStateFlow(StatisticsPeriod.Week)
    val period: StateFlow<StatisticsPeriod> = _period
    private val _range = MutableStateFlow(StatisticsPeriod.Week.range(today))
    val range: StateFlow<StatisticsRange> = _range
    private var request = 0

    init { refresh() }

    fun updateCategories(value: Map<String, AppCategory>) {
        if (overrides != value) {
            overrides = value
            refresh()
        }
    }

    fun select(period: StatisticsPeriod) {
        if (period == StatisticsPeriod.Custom) return
        _period.value = period
        _range.value = period.range(today)
        refresh()
    }

    fun selectDay(date: LocalDate) {
        if (date.isAfter(today)) return
        _period.value = StatisticsPeriod.Day
        _range.value = StatisticsRange(date, date)
        refresh()
    }

    fun selectCustom(range: StatisticsRange) {
        if (range.days > 3660 || range.endInclusive.isAfter(today)) {
            _state.value = StatisticsState.Error("Choose a range ending today or earlier, up to ten years long.")
            return
        }
        _period.value = StatisticsPeriod.Custom
        _range.value = range
        refresh()
    }

    fun refresh() {
        val token = ++request
        val selected = _range.value
        val period = _period.value
        _state.value = StatisticsState.Loading
        viewModelScope.launch {
            runCatching {
                val current = repository.statisticsDays(selected)
                val previous = repository.statisticsDays(selected.previous)
                withContext(Dispatchers.Default) {
                    StatisticsCalculator.calculate(selected, current, previous,
                        { packageName -> overrides[packageName] ?: repository.categoryFor(packageName) }, period)
                }
            }.onSuccess { if (token == request) _state.value = StatisticsState.Ready(it) }
                .onFailure { if (token == request) _state.value = StatisticsState.Error(it.message ?: "Statistics unavailable") }
        }
    }

    class Factory(private val repository: UsageRepository, private val overrides: Map<String, AppCategory>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repository, overrides) as T
    }
}
