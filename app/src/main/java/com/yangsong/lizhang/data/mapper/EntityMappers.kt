package com.yangsong.lizhang.data.mapper

import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity
import com.yangsong.lizhang.data.local.projection.ContactSummaryRow
import com.yangsong.lizhang.data.local.projection.GiftRecordWithContactRow
import com.yangsong.lizhang.data.local.projection.YearlyGiftSummaryRow
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.YearlyGiftSummary

fun ContactEntity.toDomain() = Contact(id, name, phone, relationship, notes, createdTime)
fun Contact.toEntity() = ContactEntity(id, name.trim(), phone?.trim()?.ifEmpty { null }, relationship?.trim()?.ifEmpty { null }, notes?.trim()?.ifEmpty { null }, createdTime)

fun GiftRecordEntity.toDomain() = GiftRecord(id, contactId, amountInCents, eventType, eventDate, direction, notes, createdTime, customEventName)
fun GiftRecord.toEntity() = GiftRecordEntity(id, contactId, amountInCents, eventType, eventDate, direction, notes?.trim()?.ifEmpty { null }, createdTime, customEventName)

fun ContactSummaryRow.toDomain() = ContactLedgerSummary(
    contact = contact.toDomain(),
    receivedInCents = receivedInCents,
    givenInCents = givenInCents,
    lastInteractionTime = lastInteractionTime,
)
fun GiftRecordWithContactRow.toDomain() = GiftRecordWithContact(record.toDomain(), contactName)
fun YearlyGiftSummaryRow.toDomain() = YearlyGiftSummary(year, receivedInCents, givenInCents)
