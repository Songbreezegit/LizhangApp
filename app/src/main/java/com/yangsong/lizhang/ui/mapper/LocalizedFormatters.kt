package com.yangsong.lizhang.ui.mapper

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.AppLanguage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 跟随当前页面 Configuration，语言重建及预览均使用同一套格式化规则。 */
@Composable
fun displayLocale(): Locale = LocalConfiguration.current.locales.let { locales ->
    resolveDisplayLocale((0 until locales.size()).asSequence().map { locales[it] })
}

internal fun resolveDisplayLocale(locales: Sequence<Locale>): Locale =
    locales.firstOrNull { AppLanguage.fromLanguageTag(it.toLanguageTag()) != AppLanguage.SYSTEM }
        ?: Locale.SIMPLIFIED_CHINESE

@Composable
fun displayDate(epochMillis: Long): String = DateFormatter.format(epochMillis, locale = displayLocale())

@Composable
fun displayDate(date: LocalDate): String = DateFormatter.format(date, displayLocale())

@Composable
fun displayDateTime(epochMillis: Long): String = DateFormatter.dateTime(epochMillis, displayLocale())

@Composable
fun displayAmount(amountInCents: Long): String = CurrencyFormatter.formatCents(amountInCents, displayLocale())

@Composable
fun displayMonth(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, displayLocale())

@Composable
fun displayYearMonth(year: Int, month: Int): String {
    val locale = displayLocale()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM")
    return LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
fun displayCalendarDate(year: Int, month: Int, day: Int): String =
    displayDate(LocalDate.of(year, month, day))
