package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.reminder.ReminderPlanner
import com.yangsong.lizhang.domain.repository.ReminderRepository
import java.util.Calendar
import java.util.TimeZone
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
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<DirectionRecordsUiState> = retrySignal.flow(
        source = {
            repository.observeByDirection(direction)
                .map { DirectionRecordsUiState(records = it, isLoading = false) }
        },
        onError = { DirectionRecordsUiState(isLoading = false, error = true) },
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectionRecordsUiState())

    fun retry() = retrySignal.retry()

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
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<CalendarUiState> = retrySignal.flow(
        source = {
            combine(repository.observeAll(), selection) { records, selected ->
                buildCalendarState(records, selected)
            }
        },
        onError = { CalendarUiState(isLoading = false, error = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)
    fun retry() = retrySignal.retry()

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
    val remindersEnabled: Boolean = false,
    val reminderAdvanceDays: Int = 0,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

class NotificationsViewModel(
    repository: GiftRecordRepository,
    private val reminderRepository: ReminderRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : ViewModel() {
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<NotificationsUiState> = retrySignal.flow(
        source = {
            combine(
                repository.observeAll(),
                reminderRepository.settings,
            ) { records, settings ->
                val recordsById = records.associateBy { it.record.id }
                val upcoming = ReminderPlanner.plan(
                    records = records,
                    now = now(),
                    timeZone = timeZone,
                    advanceDays = settings.advanceDays,
                    reminderHour = settings.hour,
                    reminderMinute = settings.minute,
                )
                    .mapNotNull { recordsById[it.recordId] }
                NotificationsUiState(
                    upcoming = upcoming,
                    remindersEnabled = settings.enabled,
                    reminderAdvanceDays = settings.advanceDays,
                    reminderHour = settings.hour,
                    reminderMinute = settings.minute,
                    isLoading = false,
                )
            }
        },
        onError = { NotificationsUiState(isLoading = false, error = true) },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun setRemindersEnabled(enabled: Boolean) = reminderRepository.setEnabled(enabled)
    fun retry() = retrySignal.retry()

    fun updateReminderSchedule(advanceDays: Int, hour: Int, minute: Int) =
        reminderRepository.updateSchedule(advanceDays, hour, minute)

    companion object {
        fun factory(
            repository: GiftRecordRepository,
            reminderRepository: ReminderRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotificationsViewModel(repository, reminderRepository) as T
        }
    }
}
