package com.yangsong.lizhang.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 用户主动创建的独立提醒，与礼金记录和联系人表没有关联。 */
data class IndependentReminder(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val annually: Boolean = false,
    val enabled: Boolean = true,
    val lastNotifiedDate: LocalDate? = null,
)

data class IndependentReminderPlan(val reminder: IndependentReminder, val occurrence: LocalDate, val triggerAt: Long)

object IndependentReminderPlanner {
    fun canSave(date: LocalDate, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        date.isAfter(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())

    /** 错过时刻不立即补发；年度提醒只寻找下一次尚未通知的未来时刻。 */
    fun next(reminder: IndependentReminder, settings: ReminderSettings,
        now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): IndependentReminderPlan? {
        if (!reminder.enabled) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstYear = if (reminder.annually) maxOf(today.year, reminder.date.year) else reminder.date.year
        for (year in firstYear..if (reminder.annually) firstYear + 1 else firstYear) {
            val month = reminder.date.month
            val occurrence = LocalDate.of(year, month, minOf(reminder.date.dayOfMonth, month.length(java.time.Year.isLeap(year.toLong()))))
            if (occurrence < reminder.date || occurrence < today ||
                (reminder.lastNotifiedDate != null && occurrence <= reminder.lastNotifiedDate)) continue
            val trigger = occurrence.minusDays(settings.advanceDays.toLong())
                .atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant().toEpochMilli()
            if (trigger > now) return IndependentReminderPlan(reminder, occurrence, trigger)
        }
        return null
    }
}
