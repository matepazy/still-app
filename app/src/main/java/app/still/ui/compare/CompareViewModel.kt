package app.still.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.still.data.comparison.ComparePayloadCodec
import app.still.data.settings.AppCategory
import app.still.data.usage.UsageRepository
import app.still.domain.compare.CompareEngine
import app.still.domain.compare.CompareSnapshotBuilder
import app.still.domain.model.CompareResult
import app.still.domain.model.CompareSharing
import app.still.domain.model.CompareSnapshot
import app.still.domain.model.StatisticsPeriod
import app.still.domain.model.StatisticsRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

enum class ComparePhase { Start, OwnQr, ScanFriend, ScanReply, Result, ReplyQr }

data class CompareUiState(
    val phase: ComparePhase = ComparePhase.Start,
    val range: StatisticsRange,
    val sharing: CompareSharing = CompareSharing(),
    val period: StatisticsPeriod = StatisticsPeriod.Custom,
    val code: String? = null,
    val result: CompareResult? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class CompareViewModel(
    private val repository: UsageRepository,
    private val overrides: Map<String, AppCategory>,
    inheritedRange: StatisticsRange,
) : ViewModel() {
    private val _state = MutableStateFlow(CompareUiState(
        range = inheritedRange,
        period = if (inheritedRange.days == 1L) StatisticsPeriod.Day else StatisticsPeriod.entries.firstOrNull {
            it != StatisticsPeriod.Custom && it.range(LocalDate.now()) == inheritedRange
        } ?: StatisticsPeriod.Custom,
    ))
    val state: StateFlow<CompareUiState> = _state
    private var local: CompareSnapshot? = null
    private var friend: CompareSnapshot? = null
    private var firstCodeHash: String? = null

    fun setSharing(sharing: CompareSharing) { _state.value = _state.value.copy(sharing = sharing, error = null) }
    fun selectPeriod(period: StatisticsPeriod) {
        if (period != StatisticsPeriod.Custom) _state.value = _state.value.copy(period = period, range = period.range(LocalDate.now()), error = null)
    }
    fun selectDay(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _state.value = _state.value.copy(period = StatisticsPeriod.Day, range = StatisticsRange(date, date), error = null)
    }
    fun selectCustom(range: StatisticsRange) {
        if (range.days > 3660 || range.endInclusive.isAfter(LocalDate.now())) {
            _state.value = _state.value.copy(error = "Choose a range ending today or earlier, up to ten years long.")
        } else _state.value = _state.value.copy(period = StatisticsPeriod.Custom, range = range, error = null)
    }
    fun scanFriend() {
        _state.value = _state.value.copy(phase = ComparePhase.ScanFriend, error = null)
    }
    fun scanReply() { _state.value = _state.value.copy(phase = ComparePhase.ScanReply, error = null) }
    fun showResult() { if (_state.value.result != null) _state.value = _state.value.copy(phase = ComparePhase.Result) }
    fun startOver() {
        local = null; friend = null; firstCodeHash = null
        _state.value = _state.value.copy(phase = ComparePhase.Start, code = null, result = null, error = null)
    }

    fun back(): Boolean = when (_state.value.phase) {
        ComparePhase.Start, ComparePhase.Result -> false
        ComparePhase.ScanFriend, ComparePhase.OwnQr -> { startOver(); true }
        ComparePhase.ScanReply -> { _state.value = _state.value.copy(phase = ComparePhase.OwnQr, error = null); true }
        ComparePhase.ReplyQr -> { _state.value = _state.value.copy(phase = ComparePhase.Result, error = null); true }
    }

    fun showOwnQr() {
        val chosen = _state.value
        _state.value = chosen.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                val cutoff = if (!chosen.range.endInclusive.isBefore(LocalDate.now())) Instant.now() else null
                val snapshot = build(chosen.range, chosen.sharing, cutoff)
                snapshot to ComparePayloadCodec.encode(snapshot)
            }.onSuccess { (snapshot, code) ->
                local = snapshot
                firstCodeHash = ComparePayloadCodec.fingerprint(snapshot)
                _state.value = chosen.copy(phase = ComparePhase.OwnQr, code = code)
            }.onFailure { _state.value = chosen.copy(error = it.message ?: "Could not create code") }
        }
    }

    fun onScanned(code: String) {
        val phase = _state.value.phase
        val decoded = ComparePayloadCodec.decode(code).getOrElse {
            _state.value = _state.value.copy(phase = ComparePhase.Start, error = it.message ?: "Invalid code")
            return
        }
        if (phase == ComparePhase.ScanReply) {
            runCatching {
                val own = requireNotNull(local)
                CompareEngine.result(own, decoded, firstCodeHash)
            }.onSuccess { _state.value = _state.value.copy(phase = ComparePhase.Result, result = it, error = null) }
                .onFailure { _state.value = _state.value.copy(phase = ComparePhase.OwnQr, error = it.message) }
            return
        }
        if (phase != ComparePhase.ScanFriend) return
        if (decoded.replyTo != null) {
            _state.value = _state.value.copy(phase = ComparePhase.Start, error = "Scan the first code, not a reply code.")
            return
        }
        val chosen = _state.value
        _state.value = chosen.copy(busy = true)
        viewModelScope.launch {
            runCatching {
                val cutoff = decoded.cutoffEpochMillis?.let(Instant::ofEpochMilli)
                require(cutoff == null || !Instant.now().isBefore(cutoff)) { "This phone's clock is earlier than the comparison cutoff." }
                val own = build(decoded.range, decoded.sharing, cutoff, decoded.sessionId,
                    ComparePayloadCodec.fingerprint(decoded), decoded.apps?.map { it.label })
                CompareEngine.result(own, decoded, null)
            }.onSuccess { result ->
                friend = decoded
                local = result.you
                _state.value = chosen.copy(phase = ComparePhase.Result, range = decoded.range,
                    sharing = decoded.sharing, result = result, busy = false)
            }.onFailure { _state.value = chosen.copy(phase = ComparePhase.Start, busy = false, error = it.message) }
        }
    }

    fun showReplyQr() {
        val own = local ?: return
        runCatching { ComparePayloadCodec.encode(own) }
            .onSuccess { _state.value = _state.value.copy(phase = ComparePhase.ReplyQr, code = it, error = null) }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    private suspend fun build(range: StatisticsRange, sharing: CompareSharing, cutoff: Instant?, sessionId: String? = null,
        replyTo: String? = null, requestedApps: List<String>? = null): CompareSnapshot {
        val days = repository.statisticsDays(range, cutoff)
        return withContext(Dispatchers.Default) {
            CompareSnapshotBuilder.build(range, days, sharing,
                { packageName -> overrides[packageName] ?: repository.categoryFor(packageName) },
                sessionId ?: java.util.UUID.randomUUID().toString().replace("-", ""), cutoff, replyTo, requestedApps)
        }
    }

    class Factory(private val repository: UsageRepository, private val overrides: Map<String, AppCategory>, private val range: StatisticsRange) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CompareViewModel(repository, overrides, range) as T
    }
}
