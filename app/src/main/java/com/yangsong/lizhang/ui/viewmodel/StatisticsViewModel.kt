package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    repository: GiftRecordRepository,
    now: () -> Long = System::currentTimeMillis,
    private val computeDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val currentYear = Calendar.getInstance().apply {
        timeInMillis = now()
    }.get(Calendar.YEAR)
    private val selectedYear = MutableStateFlow(currentYear)
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<StatisticsUiState> = retrySignal.flow(
        source = {
            combine(repository.observeAll(), selectedYear) { records, year -> records to year }
                .mapLatest { (records, year) ->
                    withContext(computeDispatcher) {
                        aggregateStatistics(records, year, currentYear)
                    }
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
    val calendar = Calendar.getInstance()
    val knownYears = HashSet<Int>()
    val monthlyReceived = LongArray(12)
    val monthlyGiven = LongArray(12)
    // 金额相同时保留原记录的首次出现顺序，与优化前的分组排序一致。
    val events = LinkedHashMap<EventType, LongArray>()
    val contacts = LinkedHashMap<String, Long>()
    var received = 0L
    var given = 0L

    records.forEach { item ->
        calendar.timeInMillis = item.record.eventDate
        val recordYear = calendar.get(Calendar.YEAR)
        if (recordYear in 1..currentYear) knownYears += recordYear
        if (recordYear != year) return@forEach

        val amount = item.record.amountInCents
        val monthIndex = calendar.get(Calendar.MONTH)
        val eventAmounts = events.getOrPut(item.record.eventType) { LongArray(2) }
        if (item.record.direction == GiftDirection.RECEIVED) {
            received += amount
            monthlyReceived[monthIndex] += amount
            eventAmounts[0] += amount
        } else {
            given += amount
            monthlyGiven[monthIndex] += amount
            eventAmounts[1] += amount
        }
        contacts[item.contactName] = (contacts[item.contactName] ?: 0) + amount
    }

    return StatisticsUiState(
        year = year,
        years = (knownYears + year).sortedDescending(),
        received = received,
        given = given,
        months = (0 until 12).map { monthIndex ->
            MonthStat(monthIndex + 1, monthlyReceived[monthIndex], monthlyGiven[monthIndex])
        },
        events = events
            .map { (eventType, amounts) -> EventStat(eventType, amounts[0], amounts[1]) }
            .sortedByDescending(EventStat::total),
        contacts = contacts
            .map { (name, amount) -> name to amount }
            .sortedByDescending { it.second }
            .take(5),
        isLoading = false,
    )
}
