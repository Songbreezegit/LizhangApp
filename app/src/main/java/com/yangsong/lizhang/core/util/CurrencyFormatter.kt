package com.yangsong.lizhang.core.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    fun formatCents(amountInCents: Long): String =
        NumberFormat.getCurrencyInstance(Locale.CHINA).format(amountInCents / 100.0)
}
