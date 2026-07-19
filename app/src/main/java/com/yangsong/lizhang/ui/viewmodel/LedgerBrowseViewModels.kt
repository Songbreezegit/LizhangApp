package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import java.util.Calendar
import kotlinx.coroutines.flow.*

data class DirectionRecordsUiState(
    val records: List<GiftRecordWithContact> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

class DirectionRecordsViewModel(
    direction: GiftDirection,
    repository: GiftRecordRepository,
) : ViewModel() {
    val uiState: StateFlow<DirectionRecordsUiState> = repository.observeByDirection(direction)
        .map { DirectionRecordsUiState(records = it, isLoading = false) }
        .catch { emit(DirectionRecordsUiState(isLoading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectionRecordsUiState())

    companion object {
        fun factory(direction: GiftDirection, repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DirectionRecordsViewModel(direction, repository) as T
        }
    }
}

data class CalendarUiState(
    val year: Int = 0,
    val month: Int = 0,
    val daysInMonth: Int = 0,
    val firstDayOffset: Int = 0,
    val selectedDay: Int = 1,
    val recordDays: Set<Int> = emptySet(),
    val selectedRecords: List<GiftRecordWithContact> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

private data class CalendarSelection(val monthStart: Long, val selectedDay: Int)

class CalendarViewModel(repository: GiftRecordRepository) : ViewModel() {
    private val selection = MutableStateFlow(initialSelection())

    val uiState: StateFlow<CalendarUiState> = combine(repository.observeAll(), selection) { records, selected ->
        buildCalendarState(records, selected)
    }.catch {
        emit(CalendarUiState(isLoading = false, error = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)

    fun selectDay(day: Int) {
        val days = monthCalendar(selection.value.monthStart).getActualMaximum(Calendar.DAY_OF_MONTH)
        if (day in 1..days) selection.update { it.copy(selectedDay = day) }
    }

    private fun shiftMonth(amount: Int) {
        val calendar = monthCalendar(selection.value.monthStart).apply { add(Calendar.MONTH, amount) }
        selection.value = CalendarSelection(calendar.timeInMillis, 1)
    }

    companion object {
        private fun initialSelection(): CalendarSelection {
            val now = Calendar.getInstance()
            val day = now.get(Calendar.DAY_OF_MONTH)
            now.set(Calendar.DAY_OF_MONTH, 1)
            resetTime(now)
            return CalendarSelection(now.timeInMillis, day)
        }

        private fun buildCalendarState(
            records: List<GiftRecordWithContact>,
            selection: CalendarSelection,
        ): CalendarUiState {
            val month = monthCalendar(selection.monthStart)
            val year = month.get(Calendar.YEAR)
            val monthIndex = month.get(Calendar.MONTH)
            val recordsThisMonth = records.filter { item ->
                Calendar.getInstance().apply { timeInMillis = item.record.eventDate }.let {
                    it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == monthIndex
                }
            }
            val selectedRecords = recordsThisMonth.filter { item ->
                Calendar.getInstance().apply { timeInMillis = item.record.eventDate }
                    .get(Calendar.DAY_OF_MONTH) == selection.selectedDay
            }
            return CalendarUiState(
                year = year,
                month = monthIndex + 1,
                daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH),
                firstDayOffset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7,
                selectedDay = selection.selectedDay,
                recordDays = recordsThisMonth.mapTo(mutableSetOf()) { item ->
                    Calendar.getInstance().apply { timeInMillis = item.record.eventDate }.get(Calendar.DAY_OF_MONTH)
                },
                selectedRecords = selectedRecords,
                isLoading = false,
            )
        }

        private fun monthCalendar(time: Long) = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.DAY_OF_MONTH, 1)
            resetTime(this)
        }

        private fun resetTime(calendar: Calendar) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(repository) as T
        }
    }
}

data class NotificationsUiState(
    val upcoming: List<GiftRecordWithContact> = emptyList(),
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

class NotificationsViewModel(repository: GiftRecordRepository) : ViewModel() {
    val uiState: StateFlow<NotificationsUiState> = repository.observeAll().map { records ->
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 30) }
        NotificationsUiState(
            upcoming = records.filter { it.record.eventDate in start.timeInMillis..end.timeInMillis }
                .sortedBy { it.record.eventDate },
            isLoading = false,
        )
    }.catch {
        emit(NotificationsUiState(isLoading = false, error = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    companion object {
        fun factory(repository: GiftRecordRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsViewModel(repository) as T
        }
    }
}
