package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import kotlinx.coroutines.flow.StateFlow

interface ReminderRepository {
    val settings: StateFlow<ReminderSettings>
    fun setEnabled(enabled: Boolean)
    fun updateSchedule(advanceDays: Int, hour: Int, minute: Int)
    fun synchronize(records: List<GiftRecordWithContact>)
}
