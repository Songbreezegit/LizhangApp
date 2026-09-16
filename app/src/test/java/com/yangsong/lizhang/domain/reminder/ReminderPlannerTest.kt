package com.yangsong.lizhang.domain.reminder

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `历史记录按月日生成今年上午九点提醒`() {
        val now = time(2026, 7, 21, 10)
        val record = item(1, time(2024, 7, 25, 0))

        val planned = ReminderPlanner.plan(listOf(record), now, timeZone = utc)

        assertEquals(1, planned.size)
        assertEquals(time(2026, 7, 25, 9), planned.single().triggerAt)
    }

    @Test
    fun `今天的历史日期在九点后开启时一分钟后提醒`() {
        val now = time(2026, 7, 21, 10)
        val record = item(2, time(2024, 7, 21, 0))

        val planned = ReminderPlanner.plan(listOf(record), now, timeZone = utc)

        assertEquals(now + 60_000, planned.single().triggerAt)
    }

    @Test
    fun `闰日记录在非闰年调整为二月最后一天`() {
        val now = time(2025, 2, 1, 8)
        val record = item(3, time(2024, 2, 29, 0))

        val planned = ReminderPlanner.plan(listOf(record), now, timeZone = utc)

        assertEquals(time(2025, 2, 28, 9), planned.single().triggerAt)
    }

    @Test
    fun `尚未发生的未来记录不会提前按年度日期提醒`() {
        val now = time(2026, 7, 21, 10)
        val record = item(4, time(2027, 7, 25, 0))

        val planned = ReminderPlanner.plan(listOf(record), now, timeZone = utc)

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `提前一天提醒会在事件前一天触发`() {
        val now = time(2026, 7, 21, 10)
        val record = item(5, time(2024, 7, 25, 0))

        val planned = ReminderPlanner.plan(
            listOf(record),
            now,
            timeZone = utc,
            advanceDays = 1,
            reminderHour = 8,
            reminderMinute = 30,
        )

        assertEquals(time(2026, 7, 24, 8, 30), planned.single().triggerAt)
        assertEquals(1, planned.single().advanceDays)
    }

    @Test
    fun `跨年事件的提前提醒落在上一年`() {
        val now = time(2026, 12, 1, 10)
        val record = item(6, time(2024, 1, 1, 0))

        val planned = ReminderPlanner.plan(
            listOf(record),
            now,
            timeZone = utc,
            advanceDays = 1,
        )

        assertEquals(time(2026, 12, 31, 9), planned.single().triggerAt)
    }

    @Test
    fun `提前三天提醒可以跨月计算`() {
        val now = time(2026, 7, 20, 10)
        val record = item(7, time(2024, 8, 2, 0))

        val planned = ReminderPlanner.plan(
            listOf(record),
            now,
            timeZone = utc,
            advanceDays = 3,
            reminderHour = 8,
        )

        assertEquals(time(2026, 7, 30, 8), planned.single().triggerAt)
        assertEquals(3, planned.single().advanceDays)
    }

    @Test
    fun `提前七天提醒可以跨年计算`() {
        val now = time(2026, 12, 1, 10)
        val record = item(8, time(2024, 1, 3, 0))

        val planned = ReminderPlanner.plan(
            listOf(record),
            now,
            timeZone = utc,
            advanceDays = 7,
        )

        assertEquals(time(2026, 12, 27, 9), planned.single().triggerAt)
        assertEquals(7, planned.single().advanceDays)
    }

    @Test
    fun `不支持的提前天数会被拒绝`() {
        val result = runCatching {
            ReminderSettings(advanceDays = 2)
        }

        assertTrue(result.isFailure)
    }

    private fun item(id: Long, eventDate: Long) = GiftRecordWithContact(
        record = GiftRecord(
            id = id,
            contactId = id,
            amountInCents = 20_000,
            eventType = EventType.BIRTHDAY,
            eventDate = eventDate,
            direction = GiftDirection.RECEIVED,
        ),
        contactName = "王阿姨",
    )

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
}
