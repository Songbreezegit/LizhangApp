package com.yangsong.lizhang.domain.reminder

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReminderPlannerTest {
    private val utc = ZoneId.of("UTC")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private val now = time("2026-10-03T10:00:00Z")
    private fun reminder(date: String, annually: Boolean = false) = IndependentReminder(1, "测试安排", LocalDate.parse(date), annually)
    private fun next(value: IndependentReminder, settings: ReminderSettings = ReminderSettings(), at: Long = now, zone: ZoneId = utc) =
        IndependentReminderPlanner.next(value, settings, at, zone)

    @Test fun 今天及过去日期不能保存且未来日期默认一次() {
        assertFalse(IndependentReminderPlanner.canSave(LocalDate.parse("2026-10-03"), now, utc))
        assertFalse(IndependentReminderPlanner.canSave(LocalDate.parse("2026-10-02"), now, utc))
        assertTrue(IndependentReminderPlanner.canSave(LocalDate.parse("2026-10-04"), now, utc))
        assertFalse(reminder("2026-10-04").annually)
        assertEquals(time("2026-10-04T09:00:00Z"), next(reminder("2026-10-04"))!!.triggerAt)
    }
    @Test fun 错过当天时间不补发且一次提醒不自动变年度() {
        assertNull(next(reminder("2026-10-03")))
        assertNull(next(reminder("2024-10-04")))
        assertNull(next(reminder("2026-10-04"), ReminderSettings(advanceDays = 1)))
    }
    @Test fun 已通知及停用的提醒不再发送() {
        val value = reminder("2026-10-04")
        assertNull(next(value.copy(lastNotifiedDate = value.date)))
        assertNull(next(value.copy(enabled = false)))
        assertEquals(LocalDate.parse("2027-10-04"), next(value.copy(annually = true, lastNotifiedDate = value.date))!!.occurrence)
    }
    @Test fun 年度重复错过提前时刻后安排下一年() {
        assertEquals(LocalDate.parse("2027-10-04"), next(reminder("2026-10-04", true), ReminderSettings(advanceDays = 1))!!.occurrence)
    }
    @Test fun 未来年份不能提前按今年同月日发送() {
        assertEquals(LocalDate.parse("2028-10-04"), next(reminder("2028-10-04", true))!!.occurrence)
    }
    @Test fun 闰日年度提醒使用非闰年二月最后一天() {
        val value = reminder("2024-02-29", true)
        assertEquals(LocalDate.parse("2027-02-28"), next(value)!!.occurrence)
        assertEquals(LocalDate.parse("2028-02-29"), next(value, at = time("2028-02-01T00:00:00Z"))!!.occurrence)
    }
    @Test fun 提前提醒跨月跨年并使用指定时分() {
        assertEquals(time("2026-12-27T08:30:00Z"), next(reminder("2027-01-03"), ReminderSettings(advanceDays = 7, hour = 8, minute = 30))!!.triggerAt)
        assertEquals(time("2026-10-30T09:00:00Z"), next(reminder("2026-11-02"), ReminderSettings(advanceDays = 3))!!.triggerAt)
    }
    @Test fun 系统时区与夏令时使用本地日期时间() {
        val zone = ZoneId.of("America/New_York")
        val plan = next(reminder("2027-03-14"), ReminderSettings(hour = 2, minute = 30), zone = zone)!!
        assertEquals(time("2027-03-14T07:30:00Z"), plan.triggerAt)
        assertFalse(IndependentReminderPlanner.canSave(LocalDate.parse("2026-10-03"), time("2026-10-03T23:00:00Z"), ZoneId.of("Asia/Shanghai")))
    }
    @Test fun 不支持的天数和时分被拒绝() {
        assertTrue(runCatching { ReminderSettings(advanceDays = 2) }.isFailure)
        assertTrue(runCatching { ReminderSettings(hour = 24) }.isFailure)
        assertTrue(runCatching { ReminderSettings(minute = 60) }.isFailure)
    }
}
