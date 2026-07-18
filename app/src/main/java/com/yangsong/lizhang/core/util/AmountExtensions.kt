package com.yangsong.lizhang.core.util

import java.math.BigDecimal
import java.math.RoundingMode

fun String.toCentsOrNull(): Long? = runCatching {
    BigDecimal(trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()
