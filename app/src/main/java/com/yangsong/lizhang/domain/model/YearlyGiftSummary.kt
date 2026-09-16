package com.yangsong.lizhang.domain.model

/** 单个自然年的收送礼汇总，供首页轻量加载使用。 */
data class YearlyGiftSummary(
    val year: Int,
    val receivedInCents: Long,
    val givenInCents: Long,
)
