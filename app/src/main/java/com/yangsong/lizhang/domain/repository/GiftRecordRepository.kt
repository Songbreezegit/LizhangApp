package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.YearlyGiftSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

interface GiftRecordRepository {
    fun observeRecent(limit: Int = 10): Flow<List<GiftRecordWithContact>>
    fun observeAll(): Flow<List<GiftRecordWithContact>>
    fun observeYearlySummaries(): Flow<List<YearlyGiftSummary>> = observeAll().map(::summarizeByYear)
    fun observeBetween(startInclusive: Long, endExclusive: Long): Flow<List<GiftRecordWithContact>> =
        observeAll().map { records ->
            records.filter { it.record.eventDate in startInclusive until endExclusive }
        }
    fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>>
    fun observeRecord(recordId: Long): Flow<GiftRecord?>
    fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?>
    fun observeByContact(contactId: Long): Flow<List<GiftRecord>>
    fun observeSearch(query: String): Flow<List<GiftRecordWithContact>>
    suspend fun create(record: GiftRecord): Long
    suspend fun update(record: GiftRecord)
    suspend fun delete(record: GiftRecord)
}

private fun summarizeByYear(records: List<GiftRecordWithContact>): List<YearlyGiftSummary> {
    val calendar = Calendar.getInstance()
    val summaries = LinkedHashMap<Int, LongArray>()
    records.forEach { item ->
        calendar.timeInMillis = item.record.eventDate
        val amounts = summaries.getOrPut(calendar.get(Calendar.YEAR)) { LongArray(2) }
        if (item.record.direction == com.yangsong.lizhang.domain.model.GiftDirection.RECEIVED) {
            amounts[0] += item.record.amountInCents
        } else {
            amounts[1] += item.record.amountInCents
        }
    }
    return summaries.map { (year, amounts) -> YearlyGiftSummary(year, amounts[0], amounts[1]) }
        .sortedByDescending(YearlyGiftSummary::year)
}
