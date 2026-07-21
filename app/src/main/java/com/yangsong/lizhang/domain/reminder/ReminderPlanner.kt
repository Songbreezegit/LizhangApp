package com.yangsong.lizhang.domain.reminder

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.min

data class PlannedReminder(
    val recordId: Long,
    val contactName: String,
    val eventType: EventType,
    val triggerAt: Long,
)

/** 将历史礼金日期换算为下一次年度日期提醒。 */
object ReminderPlanner {
    const val DEFAULT_LOOKAHEAD_DAYS = 30
    const val DEFAULT_REMINDER_HOUR = 9
    private const val immediateDelayMillis = 60_000L

    fun plan(
        records: List<GiftRecordWithContact>,
        now: Long = System.currentTimeMillis(),
        lookaheadDays: Int = DEFAULT_LOOKAHEAD_DAYS,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<PlannedReminder> {
        require(lookaheadDays in 1..366)
        val end = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, lookaheadDays)
        }.timeInMillis
        return records.mapNotNull { item ->
            val triggerAt = nextTrigger(item.record.eventDate, now, timeZone)
            if (triggerAt > end) null else PlannedReminder(
                recordId = item.record.id,
                contactName = item.contactName,
                eventType = item.record.eventType,
                triggerAt = triggerAt,
            )
        }.distinctBy(PlannedReminder::recordId).sortedBy(PlannedReminder::triggerAt)
    }

    private fun nextTrigger(eventDate: Long, now: Long, timeZone: TimeZone): Long {
        val event = Calendar.getInstance(timeZone).apply { timeInMillis = eventDate }
        val current = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val eventIsToday = event.sameDate(current)
        val sameMonthAndDay = event.get(Calendar.MONTH) == current.get(Calendar.MONTH) &&
            event.get(Calendar.DAY_OF_MONTH) == current.get(Calendar.DAY_OF_MONTH)

        if (eventDate >= startOfToday(current).timeInMillis) {
            val actual = dateAt(
                event.get(Calendar.YEAR),
                event.get(Calendar.MONTH),
                event.get(Calendar.DAY_OF_MONTH),
                timeZone,
            )
            return if (eventIsToday && actual <= now) now + immediateDelayMillis else actual
        }

        var annual = dateAt(
            current.get(Calendar.YEAR),
            event.get(Calendar.MONTH),
            event.get(Calendar.DAY_OF_MONTH),
            timeZone,
        )
        if (sameMonthAndDay && annual <= now) return now + immediateDelayMillis
        if (annual < startOfToday(current).timeInMillis) {
            annual = dateAt(
                current.get(Calendar.YEAR) + 1,
                event.get(Calendar.MONTH),
                event.get(Calendar.DAY_OF_MONTH),
                timeZone,
            )
        }
        return annual
    }

    private fun dateAt(year: Int, month: Int, day: Int, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, DEFAULT_REMINDER_HOUR)
            set(Calendar.DAY_OF_MONTH, min(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }.timeInMillis

    private fun startOfToday(calendar: Calendar) = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.sameDate(other: Calendar): Boolean =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}
