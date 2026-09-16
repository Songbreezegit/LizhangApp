package com.yangsong.lizhang.core.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val formatter = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(Locale.CHINA).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
    }

    /** 金额展示常位于滚动列表中，线程内复用非线程安全的 NumberFormat。 */
    fun formatCents(amountInCents: Long): String =
        requireNotNull(formatter.get()).format(amountInCents / 100.0)
}
