package com.yangsong.lizhang.data.local.projection

import androidx.room.Embedded
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity

data class GiftRecordWithContactRow(
    @Embedded val record: GiftRecordEntity,
    val contactName: String,
)
