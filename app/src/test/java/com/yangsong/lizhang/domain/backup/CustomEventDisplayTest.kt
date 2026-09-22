package com.yangsong.lizhang.domain.backup

import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.export.*
import com.yangsong.lizhang.domain.reminder.ReminderPlanner
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class CustomEventDisplayTest {
    private val record = GiftRecord(contactId = 1, amountInCents = 10000, eventType = EventType.OTHER,
        eventDate = 1000, direction = GiftDirection.GIVEN, customEventName = "升学宴")
    @Test fun 自定义与预设名称统一且统计枚举稳定() {
        assertEquals("升学宴", record.eventDisplayName())
        assertEquals("其他", record.copy(customEventName = " ").eventDisplayName())
        assertEquals("生日", record.copy(eventType = EventType.BIRTHDAY).eventDisplayName())
        assertEquals(EventType.OTHER, record.eventType)
    }
    @Test fun CSV与Excel导出真实自定义名称() {
        val items = listOf(GiftRecordWithContact(record, "示例联系人"))
        assertTrue(GiftRecordCsvFormatter.format(items).contains("升学宴"))
        val bytes = GiftRecordXlsxFormatter.format(items)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var found = false
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    assertTrue(zip.readBytes().toString(Charsets.UTF_8).contains("升学宴"))
                    found = true
                }
            }
            assertTrue(found)
        }
    }
    @Test fun 提醒规划保留自定义名称() {
        val items = listOf(GiftRecordWithContact(record, "示例联系人"))
        assertEquals("升学宴", ReminderPlanner.plan(items, lookaheadDays = 366).single().customEventName)
    }
}
