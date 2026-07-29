package com.yangsong.lizhang.domain.model

data class ContactLedgerSummary(
    val contact: Contact,
    val receivedInCents: Long,
    val givenInCents: Long,
    val lastInteractionTime: Long = contact.createdTime,
) {
    val netInCents: Long get() = receivedInCents - givenInCents
}
