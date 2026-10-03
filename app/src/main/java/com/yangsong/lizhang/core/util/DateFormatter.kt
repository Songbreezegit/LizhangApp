package com.yangsong.lizhang.core.util

import java.text.SimpleDateFormat
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object DateFormatter {
    private val formatters = ConcurrentHashMap<String, ThreadLocal<DateFormat>>()

    /** 无时区的提醒日期与日历日期共用展示格式，通知显式传入应用语言。 */
    fun format(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

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
