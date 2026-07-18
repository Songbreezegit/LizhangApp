package com.yangsong.lizhang.domain.model

data class GiftRecord(
    val id: Long = 0,
    val contactId: Long,
    /** 金额以“分”保存，避免浮点计算产生误差。 */
    val amountInCents: Long,
    val eventType: EventType,
    val eventDate: Long,
    val direction: GiftDirection,
    val notes: String? = null,
    val createdTime: Long = System.currentTimeMillis(),
)
