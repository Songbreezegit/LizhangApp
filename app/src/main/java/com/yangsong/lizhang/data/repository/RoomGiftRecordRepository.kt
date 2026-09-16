package com.yangsong.lizhang.data.repository

import com.yangsong.lizhang.data.local.dao.GiftRecordDao
import com.yangsong.lizhang.data.mapper.toDomain
import com.yangsong.lizhang.data.mapper.toEntity
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.YearlyGiftSummary
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomGiftRecordRepository(private val giftRecordDao: GiftRecordDao) : GiftRecordRepository {
    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> =
        giftRecordDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<GiftRecordWithContact>> =
        giftRecordDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeYearlySummaries(): Flow<List<YearlyGiftSummary>> =
        giftRecordDao.observeYearlySummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<GiftRecordWithContact>> =
        giftRecordDao.observeBetween(startInclusive, endExclusive).map { rows -> rows.map { it.toDomain() } }

    override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> =
        giftRecordDao.observeByDirection(direction).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecord(recordId: Long): Flow<GiftRecord?> =
        giftRecordDao.observeById(recordId).map { it?.toDomain() }

    override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> =
        giftRecordDao.observeWithContactById(recordId).map { it?.toDomain() }

    override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> =
        giftRecordDao.observeByContact(contactId).map { entities -> entities.map { it.toDomain() } }

    override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> =
        giftRecordDao.observeSearch(query.trim()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(record: GiftRecord): Long = giftRecordDao.insert(record.toEntity())
    override suspend fun update(record: GiftRecord) = giftRecordDao.update(record.toEntity())
    override suspend fun delete(record: GiftRecord) = giftRecordDao.delete(record.toEntity())
}
