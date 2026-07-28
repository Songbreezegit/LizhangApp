package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderLaunchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `提醒记录存在时返回详情导航结果`() = runTest(dispatcher) {
        val viewModel = ReminderLaunchViewModel(ReminderFakeGiftRecordRepository(RECORD))
        val result = async { viewModel.results.first() }

        viewModel.resolve(RECORD.id)

        assertEquals(ReminderLaunchResult.OpenRecord(RECORD.id), result.await())
    }

    @Test
    fun `提醒记录已删除时返回安全回退结果`() = runTest(dispatcher) {
        val viewModel = ReminderLaunchViewModel(ReminderFakeGiftRecordRepository(null))
        val result = async { viewModel.results.first() }

        viewModel.resolve(999)

        assertEquals(ReminderLaunchResult.RecordUnavailable, result.await())
    }

    private companion object {
        val RECORD = GiftRecord(
            id = 8,
            contactId = 3,
            amountInCents = 20_000,
            eventType = EventType.BIRTHDAY,
            eventDate = 1_752_787_200_000,
            direction = GiftDirection.RECEIVED,
        )
    }
}

private class ReminderFakeGiftRecordRepository(
    private val record: GiftRecord?,
) : GiftRecordRepository {
    override fun observeRecord(recordId: Long): Flow<GiftRecord?> =
        flowOf(record?.takeIf { it.id == recordId })

    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> = flowOf(emptyList())
    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flowOf(emptyList())
    override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> =
        flowOf(emptyList())
    override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> = flowOf(null)
    override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> = flowOf(emptyList())
    override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> = flowOf(emptyList())
    override suspend fun create(record: GiftRecord): Long = record.id
    override suspend fun update(record: GiftRecord) = Unit
    override suspend fun delete(record: GiftRecord) = Unit
}
