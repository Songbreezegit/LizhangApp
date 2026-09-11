package com.yangsong.lizhang.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object DateFormatter {
    private const val DEFAULT_PATTERN = "yyyy-MM-dd"

    private val formatters = ConcurrentHashMap<String, ThreadLocal<SimpleDateFormat>>()

    /** 列表渲染频繁调用，按格式在线程内复用格式器，避免每个条目都创建对象。 */
    fun format(epochMillis: Long, pattern: String = DEFAULT_PATTERN): String =
        formatters.getOrPut(pattern) {
            ThreadLocal.withInitial { SimpleDateFormat(pattern, Locale.CHINA) }
        }.let { requireNotNull(it.get()) }.apply { timeZone = TimeZone.getDefault() }.format(Date(epochMillis))
}
