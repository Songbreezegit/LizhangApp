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

    /** 新建必须选未来日期；已有年度提醒的历史锚点仍可用于未来年度安排。 */
    fun canSave(reminder: IndependentReminder, existing: IndependentReminder? = null,
        settings: ReminderSettings = ReminderSettings(), now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (canSave(reminder.date, now, zone)) return true
        if (!reminder.annually || existing?.annually != true || reminder.id <= 0 || existing.id != reminder.id) return false
        // 停用只影响调度，不应禁止编辑；仍检查是否存在尚未消费的未来触发时刻。
        return next(reminder.copy(enabled = true), settings, now, zone) != null
    }

    /** 错过时刻不立即补发；年度提醒只寻找下一次尚未通知的未来时刻。 */
    fun next(reminder: IndependentReminder, settings: ReminderSettings,
        now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): IndependentReminderPlan? {
        if (!reminder.enabled) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstYear = if (reminder.annually) maxOf(today.year, reminder.date.year,
            reminder.lastNotifiedDate?.year ?: today.year) else reminder.date.year
        // 最多提前七天，年末可能连续错过今年与明年年初，须继续查找后年。
        // 从最后消费年份开始，有限检查四个年份，避免损坏数据造成无限搜索。
        val lastYear = if (reminder.annually) minOf(firstYear.toLong() + 3, java.time.Year.MAX_VALUE.toLong()).toInt() else firstYear
        for (year in firstYear..lastYear) {
            val month = reminder.date.month
            val occurrence = LocalDate.of(year, month, minOf(reminder.date.dayOfMonth, month.length(java.time.Year.isLeap(year.toLong()))))
            if (occurrence < reminder.date || occurrence < today ||
                (reminder.lastNotifiedDate != null && occurrence <= reminder.lastNotifiedDate)) continue
            val trigger = runCatching { occurrence.minusDays(settings.advanceDays.toLong())
                .atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant().toEpochMilli() }.getOrNull() ?: continue
            if (trigger > now) return IndependentReminderPlan(reminder, occurrence, trigger)
        }
        return null
    }
}
