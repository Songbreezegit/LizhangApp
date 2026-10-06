package com.yangsong.lizhang.core.util

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDateFormatterTest {
    private val date = LocalDate.of(2026, 10, 4)

    @Test fun 提醒日期使用四种应用语言的中等日期格式() {
        val expected = mapOf("zh-CN" to "2026年10月4日", "en" to "Oct 4, 2026",
            "ja" to "2026/10/04", "ko" to "2026. 10. 4.")
        expected.forEach { (tag, text) -> assertEquals(tag, text, DateFormatter.format(date, Locale.forLanguageTag(tag))) }
    }

    @Test fun 显式应用语言不受系统默认语言影响() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.KOREAN)
            assertEquals("Oct 4, 2026", DateFormatter.format(date, Locale.ENGLISH))
        } finally { Locale.setDefault(previous) }
    }
}
