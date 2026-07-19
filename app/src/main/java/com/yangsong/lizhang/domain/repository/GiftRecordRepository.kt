package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.GiftDirection
import kotlinx.coroutines.flow.Flow

interface GiftRecordRepository {
    fun observeRecent(limit: Int = 10): Flow<List<GiftRecordWithContact>>
    fun observeAll(): Flow<List<GiftRecordWithContact>>
    fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>>
    fun observeByContact(contactId: Long): Flow<List<GiftRecord>>
    fun observeSearch(query: String): Flow<List<GiftRecordWithContact>>
    suspend fun create(record: GiftRecord): Long
    suspend fun update(record: GiftRecord)
    suspend fun delete(record: GiftRecord)
}
