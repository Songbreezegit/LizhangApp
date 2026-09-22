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
    val advanceDays: Int = 0,
    val customEventName: String? = null,
)

val supportedReminderAdvanceDays = listOf(0, 1, 3, 7)

fun isSupportedReminderAdvanceDays(days: Int): Boolean = days in supportedReminderAdvanceDays

data class ReminderSettings(
    val enabled: Boolean = false,
    val advanceDays: Int = 0,
    val hour: Int = 9,
    val minute: Int = 0,
) {
    init {
        require(isSupportedReminderAdvanceDays(advanceDays))
        require(hour in 0..23)
        require(minute in 0..59)
    }
}

/** 将历史礼金日期换算为下一次年度日期提醒。 */
object ReminderPlanner {
    const val DEFAULT_LOOKAHEAD_DAYS = 30
    private const val immediateDelayMillis = 60_000L

    fun plan(
        records: List<GiftRecordWithContact>,
        now: Long = System.currentTimeMillis(),
        lookaheadDays: Int = DEFAULT_LOOKAHEAD_DAYS,
        timeZone: TimeZone = TimeZone.getDefault(),
        advanceDays: Int = 0,
        reminderHour: Int = 9,
        reminderMinute: Int = 0,
    ): List<PlannedReminder> {
        require(lookaheadDays in 1..366)
        require(isSupportedReminderAdvanceDays(advanceDays))
        require(reminderHour in 0..23)
        require(reminderMinute in 0..59)
        val end = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, lookaheadDays)
        }.timeInMillis
        return records.mapNotNull { item ->
            val triggerAt = nextTrigger(
                item.record.eventDate,
                now,
                timeZone,
                advanceDays,
                reminderHour,
                reminderMinute,
            )
            if (triggerAt > end) null else PlannedReminder(
                recordId = item.record.id,
                contactName = item.contactName,
                eventType = item.record.eventType,
                customEventName = item.record.customEventName,
                triggerAt = triggerAt,
                advanceDays = advanceDays,
            )
        }.distinctBy(PlannedReminder::recordId).sortedBy(PlannedReminder::triggerAt)
    }

    private fun nextTrigger(
        eventDate: Long,
        now: Long,
        timeZone: TimeZone,
        advanceDays: Int,
        reminderHour: Int,
        reminderMinute: Int,
    ): Long {
        val event = Calendar.getInstance(timeZone).apply { timeInMillis = eventDate }
        val current = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val eventIsToday = event.sameDate(current)
        val sameMonthAndDay = event.get(Calendar.MONTH) == current.get(Calendar.MONTH) &&
            event.get(Calendar.DAY_OF_MONTH) == current.get(Calendar.DAY_OF_MONTH)

        if (eventDate >= startOfToday(current).timeInMillis) {
            val actual = triggerAt(
                event.get(Calendar.YEAR),
                event.get(Calendar.MONTH),
                event.get(Calendar.DAY_OF_MONTH),
                timeZone,
                advanceDays,
                reminderHour,
                reminderMinute,
            )
            return if (eventIsToday && actual <= now) now + immediateDelayMillis else actual
        }

        var annual = triggerAt(
            current.get(Calendar.YEAR),
            event.get(Calendar.MONTH),
            event.get(Calendar.DAY_OF_MONTH),
            timeZone,
            advanceDays,
            reminderHour,
            reminderMinute,
        )
        if (sameMonthAndDay && advanceDays == 0 && annual <= now) return now + immediateDelayMillis
        if (annual < startOfToday(current).timeInMillis) {
            annual = triggerAt(
                current.get(Calendar.YEAR) + 1,
                event.get(Calendar.MONTH),
                event.get(Calendar.DAY_OF_MONTH),
                timeZone,
                advanceDays,
                reminderHour,
                reminderMinute,
            )
        }
        return annual
    }

    private fun triggerAt(
        year: Int,
        month: Int,
        day: Int,
        timeZone: TimeZone,
        advanceDays: Int,
        reminderHour: Int,
        reminderMinute: Int,
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
            set(Calendar.DAY_OF_MONTH, min(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
            add(Calendar.DAY_OF_YEAR, -advanceDays)
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
