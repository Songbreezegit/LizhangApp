package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.reminder.IndependentReminder
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface ReminderRepository {
    val settings: StateFlow<ReminderSettings>
    val reminders: StateFlow<List<IndependentReminder>>
    fun setEnabled(enabled: Boolean)
    fun updateSchedule(advanceDays: Int, hour: Int, minute: Int)
    fun save(reminder: IndependentReminder)
    fun delete(id: Long)
    fun setReminderEnabled(id: Long, enabled: Boolean)
    fun markNotified(id: Long, occurrence: LocalDate)
    fun synchronize()
}
