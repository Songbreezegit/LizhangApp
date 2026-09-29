package com.yangsong.lizhang.core.util

import java.text.NumberFormat
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

object CurrencyFormatter {
    private val formatters = ConcurrentHashMap<Locale, ThreadLocal<NumberFormat>>()

    /** 只本地化展示格式，货币始终为人民币，金额仍以分保存。 */
    fun formatCents(amountInCents: Long, locale: Locale = Locale.getDefault()): String =
        requireNotNull(formatters.getOrPut(locale) {
            ThreadLocal.withInitial {
                NumberFormat.getCurrencyInstance(locale).apply {
                    currency = Currency.getInstance("CNY")
                    maximumFractionDigits = 2
                    minimumFractionDigits = 2
                }
            }
        }.get()).format(BigDecimal.valueOf(amountInCents, 2))
}
