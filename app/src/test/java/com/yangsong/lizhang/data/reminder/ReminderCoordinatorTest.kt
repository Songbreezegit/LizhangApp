package com.yangsong.lizhang.data.reminder

import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.ReminderRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {
    @Test
    fun `提醒关闭时启动和刷新都不读取礼金表`() = runTest {
        val gifts = CountingGiftRecordRepository()
        val reminders = FakeReminderRepository()
        val coordinator = ReminderCoordinator(gifts, reminders, backgroundScope)

        coordinator.start()
        runCurrent()
        coordinator.refresh()
        runCurrent()

        assertEquals(0, gifts.observeAllSubscriptions)
        assertEquals(0, reminders.synchronizedRecords.size)
    }

    @Test
    fun `提醒开启后订阅礼金变化并执行同步`() = runTest {
        val gifts = CountingGiftRecordRepository(
            listOf(
                GiftRecordWithContact(
                    GiftRecord(
                        id = 1,
                        contactId = 1,
                        amountInCents = 10_000,
                        eventType = EventType.OTHER,
                        eventDate = 1,
                        direction = GiftDirection.RECEIVED,
                    ),
                    "测试联系人",
                ),
            ),
        )
        val reminders = FakeReminderRepository()
        val coordinator = ReminderCoordinator(gifts, reminders, backgroundScope)

        coordinator.start()
        reminders.setEnabled(true)
        runCurrent()

        assertEquals(1, gifts.observeAllSubscriptions)
        assertEquals(listOf(1L), reminders.synchronizedRecords.single().map { it.record.id })
    }
}

private class CountingGiftRecordRepository(
    private val records: List<GiftRecordWithContact> = emptyList(),
) : GiftRecordRepository {
    var observeAllSubscriptions = 0

    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flow {
        observeAllSubscriptions += 1
        emit(records)
        awaitCancellation()
    }

    override fun observeRecent(limit: Int) = flowOf(records.take(limit))
    override fun observeByDirection(direction: GiftDirection) = flowOf(
        records.filter { it.record.direction == direction },
    )
    override fun observeRecord(recordId: Long) = flowOf(
        records.firstOrNull { it.record.id == recordId }?.record,
    )
    override fun observeRecordWithContact(recordId: Long) = flowOf(
        records.firstOrNull { it.record.id == recordId },
    )
    override fun observeByContact(contactId: Long) = flowOf(
        records.filter { it.record.contactId == contactId }.map(GiftRecordWithContact::record),
    )
    override fun observeSearch(query: String) = flowOf(records)
    override suspend fun create(record: GiftRecord) = record.id
    override suspend fun update(record: GiftRecord) = Unit
    override suspend fun delete(record: GiftRecord) = Unit
}

private class FakeReminderRepository : ReminderRepository {
    override val settings = MutableStateFlow(ReminderSettings())
    val synchronizedRecords = mutableListOf<List<GiftRecordWithContact>>()

    override fun setEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(enabled = enabled)
    }

    override fun updateSchedule(advanceDays: Int, hour: Int, minute: Int) {
        settings.value = settings.value.copy(
            advanceDays = advanceDays,
            hour = hour,
            minute = minute,
        )
    }

    override fun synchronize(records: List<GiftRecordWithContact>) {
        synchronizedRecords += records
    }
}
