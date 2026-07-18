package com.yangsong.lizhang.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatter {
    private const val DEFAULT_PATTERN = "yyyy-MM-dd"

    fun format(epochMillis: Long, pattern: String = DEFAULT_PATTERN): String =
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(epochMillis))
}
