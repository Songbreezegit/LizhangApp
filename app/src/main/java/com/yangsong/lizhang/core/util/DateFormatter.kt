package com.yangsong.lizhang.core.util

import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object DateFormatter {
    private val formatters = ConcurrentHashMap<String, ThreadLocal<DateFormat>>()

    /** 展示日期随语言变化；显式格式用于导出和文件名，保持原有数据协议。 */
    fun format(epochMillis: Long, pattern: String? = null, locale: Locale = Locale.getDefault()): String {
        val formattingLocale = if (pattern == null) locale else Locale.ROOT
        val key = "${formattingLocale.toLanguageTag()}:$pattern"
        return requireNotNull(formatters.getOrPut(key) {
            ThreadLocal.withInitial {
                if (pattern == null) DateFormat.getDateInstance(DateFormat.MEDIUM, formattingLocale)
                else SimpleDateFormat(pattern, formattingLocale)
            }
        }.get()).apply { timeZone = TimeZone.getDefault() }.format(Date(epochMillis))
    }

    fun dateTime(epochMillis: Long, locale: Locale): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMillis))
}
