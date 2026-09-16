package com.yangsong.lizhang.data.local.projection

import androidx.room.Embedded
import com.yangsong.lizhang.data.local.entity.ContactEntity

data class ContactSummaryRow(
    @Embedded val contact: ContactEntity,
    val receivedInCents: Long,
    val givenInCents: Long,
    val lastInteractionTime: Long,
)
