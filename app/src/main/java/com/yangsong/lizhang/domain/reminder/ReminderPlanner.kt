package com.yangsong.lizhang.domain.reminder

val supportedReminderAdvanceDays = listOf(0, 1, 3, 7)
fun isSupportedReminderAdvanceDays(days: Int): Boolean = days in supportedReminderAdvanceDays

data class ReminderSettings(val enabled: Boolean = false, val advanceDays: Int = 0, val hour: Int = 9, val minute: Int = 0) {
    init { require(isSupportedReminderAdvanceDays(advanceDays)); require(hour in 0..23); require(minute in 0..59) }
}
