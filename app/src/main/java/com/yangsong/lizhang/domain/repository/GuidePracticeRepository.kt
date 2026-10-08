package com.yangsong.lizhang.domain.repository

/** 演示会话独立仓储，只向既有页面提供练习数据，不接触真实账本。 */
interface GuidePracticeRepository {
    val contactRepository: ContactRepository
    val giftRecordRepository: GiftRecordRepository
    val contactId: Long
    val recordId: Long

    /** 丢弃当前演示中的联系人和礼金记录。 */
    fun clear()
}
