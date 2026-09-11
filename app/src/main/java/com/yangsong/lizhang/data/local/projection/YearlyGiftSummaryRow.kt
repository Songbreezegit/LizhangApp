package com.yangsong.lizhang.data.local.projection

/** Room 聚合查询结果，避免首页为年度汇总读取完整礼金列表。 */
data class YearlyGiftSummaryRow(
    val year: Int,
    val receivedInCents: Long,
    val givenInCents: Long,
)
