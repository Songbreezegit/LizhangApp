package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.reminder.IndependentReminder
import com.yangsong.lizhang.domain.reminder.IndependentReminderPlanner
import com.yangsong.lizhang.domain.repository.ReminderRepository
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.coroutineContext

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

/** 每次月数据变化只建立一次日期索引，选日直接读取对应列表。 */
private data class CalendarMonthData(
    val monthStart: Long,
    val year: Int,
    val month: Int,
    val daysInMonth: Int,
    val firstDayOffset: Int,
    val recordDays: Set<Int>,
    val recordsByDay: Map<Int, List<GiftRecordWithContact>>,
) {
    fun selectedState(day: Int) = CalendarUiState(
        year = year,
        month = month,
        daysInMonth = daysInMonth,
        firstDayOffset = firstDayOffset,
        selectedDay = day,
        recordDays = recordDays,
        selectedRecords = recordsByDay[day].orEmpty(),
        isLoading = false,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(repository: GiftRecordRepository) : ViewModel() {
    private val selection = MutableStateFlow(initialSelection())
    private val retrySignal = RetrySignal()

    val uiState: StateFlow<CalendarUiState> = retrySignal.flow(
        source = {
            selection.map { it.monthStart }.distinctUntilChanged().flatMapLatest { monthStart ->
                repository.observeBetween(monthStart, nextMonthStart(monthStart))
                    .mapLatest { records -> buildCalendarMonth(records, monthStart) }
                    .flowOn(Dispatchers.Default)
                    .combine(selection) { month, selected ->
                        // 翻月期间旧查询结果不能与新月份的选日拼接。
                        if (month.monthStart == selected.monthStart) month.selectedState(selected.selectedDay) else null
                    }
                    .filterNotNull()
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

        private suspend fun buildCalendarMonth(
            records: List<GiftRecordWithContact>,
            monthStart: Long,
        ): CalendarMonthData {
            val month = monthCalendar(monthStart)
            val year = month.get(Calendar.YEAR)
            val monthIndex = month.get(Calendar.MONTH)
            val recordsByDay = linkedMapOf<Int, MutableList<GiftRecordWithContact>>()
            val recordCalendar = Calendar.getInstance()
            records.forEach { item ->
                coroutineContext.ensureActive()
                recordCalendar.timeInMillis = item.record.eventDate
                val day = recordCalendar.get(Calendar.DAY_OF_MONTH)
                recordsByDay.getOrPut(day) { ArrayList() }.add(item)
            }
            return CalendarMonthData(
                monthStart = monthStart,
                year = year,
                month = monthIndex + 1,
                daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH),
                firstDayOffset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7,
                recordDays = recordsByDay.keys.toSet(),
                recordsByDay = recordsByDay,
            )
        }

        private fun monthCalendar(time: Long) = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.DAY_OF_MONTH, 1)
            resetTime(this)
        }

        private fun nextMonthStart(time: Long) = monthCalendar(time).apply {
            add(Calendar.MONTH, 1)
        }.timeInMillis

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
    val reminders: List<IndependentReminder> = emptyList(),
    val remindersEnabled: Boolean = false,
    val reminderAdvanceDays: Int = 0,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val isLoading: Boolean = true,
    val error: Boolean = false,
)

class NotificationsViewModel(private val reminderRepository: ReminderRepository) : ViewModel() {
    val uiState: StateFlow<NotificationsUiState> = combine(reminderRepository.reminders, reminderRepository.settings) { reminders, settings ->
        NotificationsUiState(reminders = reminders.sortedWith(compareBy<IndependentReminder> { !it.enabled }.thenBy { it.date }.thenBy { it.id }),
            remindersEnabled = settings.enabled, reminderAdvanceDays = settings.advanceDays,
            reminderHour = settings.hour, reminderMinute = settings.minute, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())
    fun setRemindersEnabled(enabled: Boolean) = reminderRepository.setEnabled(enabled)
    fun retry() = reminderRepository.synchronize()
    fun updateReminderSchedule(advanceDays: Int, hour: Int, minute: Int) = reminderRepository.updateSchedule(advanceDays, hour, minute)
    fun saveReminder(value: IndependentReminder): Boolean = runCatching {
        require(IndependentReminderPlanner.canSave(value,
            reminderRepository.reminders.value.firstOrNull { it.id == value.id }, reminderRepository.settings.value))
        reminderRepository.save(value)
    }.isSuccess
    fun deleteReminder(id: Long) = reminderRepository.delete(id)
    fun setReminderEnabled(id: Long, enabled: Boolean) = reminderRepository.setReminderEnabled(id, enabled)
    companion object {
        fun factory(reminderRepository: ReminderRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsViewModel(reminderRepository) as T
        }
    }
}
