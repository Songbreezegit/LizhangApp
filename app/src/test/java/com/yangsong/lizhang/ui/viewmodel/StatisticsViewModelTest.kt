package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatisticsViewModelTest {
    @Test
    fun `年度统计按月份事件和收送方向分别汇总`() {
        val records = listOf(
            record(1, "张三", 10_000, EventType.WEDDING, GiftDirection.RECEIVED, date(2026, 1, 2)),
            record(2, "张三", 4_000, EventType.WEDDING, GiftDirection.GIVEN, date(2026, 1, 8)),
            record(3, "李四", 5_000, EventType.BIRTHDAY, GiftDirection.RECEIVED, date(2026, 2, 3)),
            record(4, "旧记录", 99_000, EventType.OTHER, GiftDirection.RECEIVED, date(2025, 6, 1)),
            record(5, "未来记录", 88_000, EventType.OTHER, GiftDirection.RECEIVED, date(2027, 6, 1)),
        )

        val state = aggregateStatistics(records, year = 2026, currentYear = 2026)

        assertEquals(15_000L, state.received)
        assertEquals(4_000L, state.given)
        assertEquals(11_000L, state.net)
        assertEquals(10_000L, state.months.single { it.month == 1 }.received)
        assertEquals(4_000L, state.months.single { it.month == 1 }.given)
        assertEquals(5_000L, state.months.single { it.month == 2 }.received)

        val wedding = state.events.single { it.eventType == EventType.WEDDING }
        assertEquals(10_000L, wedding.received)
        assertEquals(4_000L, wedding.given)
        assertEquals(EventType.WEDDING, state.events.first().eventType)

        assertEquals(listOf(2026, 2025), state.years)
        assertFalse(state.years.contains(2027))
        assertEquals("张三", state.contacts.first().first)
        assertEquals(14_000L, state.contacts.first().second)
    }

    private fun record(
        id: Long,
        contactName: String,
        amount: Long,
        eventType: EventType,
        direction: GiftDirection,
        eventDate: Long,
    ) = GiftRecordWithContact(
        record = GiftRecord(
            id = id,
            contactId = id,
            amountInCents = amount,
            eventType = eventType,
            eventDate = eventDate,
            direction = direction,
        ),
        contactName = contactName,
    )

    private fun date(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis
}
