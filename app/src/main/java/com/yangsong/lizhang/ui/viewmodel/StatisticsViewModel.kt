package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import java.util.Calendar
import kotlinx.coroutines.flow.*

data class MonthStat(
    val month: Int,
    val received: Long,
    val given: Long,
)

data class EventStat(
    val eventType: EventType,
    val received: Long,
    val given: Long,
) {
    val total: Long get() = received + given
}

data class StatisticsUiState(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val years: List<Int> = listOf(year),
    val received: Long = 0,
    val given: Long = 0,
    val months: List<MonthStat> = emptyList(),
    val events: List<EventStat> = emptyList(),
    val contacts: List<Pair<String, Long>> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
) {
    val net get() = received - given
}

class StatisticsViewModel(
    repository: GiftRecordRepository,
    now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val currentYear = Calendar.getInstance().apply {
        timeInMillis = now()
    }.get(Calendar.YEAR)
    private val selectedYear = MutableStateFlow(currentYear)
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<StatisticsUiState> = retrySignal.flow(
        source = {
            combine(repository.observeAll(), selectedYear) { records, year ->
                aggregateStatistics(records, year, currentYear)
            }
        },
        onError = { StatisticsUiState(isLoading = false, error = true) },
    )
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StatisticsUiState(year = currentYear, years = listOf(currentYear)),
        )

    fun selectYear(year: Int) {
        if (year in uiState.value.years) selectedYear.value = year
    }

    fun retry() = retrySignal.retry()

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                StatisticsViewModel(repository) as T
        }
    }
}

internal fun aggregateStatistics(
    records: List<GiftRecordWithContact>,
    year: Int,
    currentYear: Int,
): StatisticsUiState {
    fun recordYear(item: GiftRecordWithContact) = Calendar.getInstance().apply {
        timeInMillis = item.record.eventDate
    }.get(Calendar.YEAR)

    fun recordMonth(item: GiftRecordWithContact) = Calendar.getInstance().apply {
        timeInMillis = item.record.eventDate
    }.get(Calendar.MONTH) + 1

    val filtered = records.filter { recordYear(it) == year }
    val received = filtered
        .filter { it.record.direction == GiftDirection.RECEIVED }
        .sumOf { it.record.amountInCents }
    val given = filtered
        .filter { it.record.direction == GiftDirection.GIVEN }
        .sumOf { it.record.amountInCents }
    val months = (1..12).map { month ->
        val monthRecords = filtered.filter { recordMonth(it) == month }
        MonthStat(
            month = month,
            received = monthRecords
                .filter { it.record.direction == GiftDirection.RECEIVED }
                .sumOf { it.record.amountInCents },
            given = monthRecords
                .filter { it.record.direction == GiftDirection.GIVEN }
                .sumOf { it.record.amountInCents },
        )
    }
    return StatisticsUiState(
        year = year,
        years = (records.map(::recordYear).filter { it in 1..currentYear } + year)
            .distinct()
            .sortedDescending(),
        received = received,
        given = given,
        months = months,
        events = filtered
            .groupBy { it.record.eventType }
            .map { (eventType, eventRecords) ->
                EventStat(
                    eventType = eventType,
                    received = eventRecords
                        .filter { it.record.direction == GiftDirection.RECEIVED }
                        .sumOf { it.record.amountInCents },
                    given = eventRecords
                        .filter { it.record.direction == GiftDirection.GIVEN }
                        .sumOf { it.record.amountInCents },
                )
            }
            .sortedByDescending(EventStat::total),
        contacts = filtered
            .groupBy { it.contactName }
            .map { (name, contactRecords) ->
                name to contactRecords.sumOf { it.record.amountInCents }
            }
            .sortedByDescending { it.second }
            .take(5),
        isLoading = false,
    )
}
