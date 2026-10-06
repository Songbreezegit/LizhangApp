package com.yangsong.lizhang.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactBulkDeleteRepositoryInstrumentedTest {
    private lateinit var database: LiZhangDatabase
    private lateinit var contacts: RoomContactRepository
    private lateinit var gifts: RoomGiftRecordRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LiZhangDatabase::class.java).build()
        contacts = RoomContactRepository(database.contactDao())
        gifts = RoomGiftRecordRepository(database.giftRecordDao())
    }
    @After fun teardown() = database.close()
    private suspend fun contact(name: String) = contacts.create(Contact(name = name))
    private suspend fun gift(id: Long) = gifts.create(GiftRecord(contactId = id, amountInCents = 100,
        eventType = EventType.OTHER, eventDate = 1_700_000_000_000, direction = GiftDirection.GIVEN))

    @Test fun 无礼金批量删除通知Flow且不影响未选联系人() = runBlocking {
        val first = contact("待删除甲")
        val second = contact("待删除乙")
        val keep = contact("保留测试")
        val ready = CompletableDeferred<Unit>()
        val refreshed = async { withTimeout(10_000) {
            contacts.observeContacts().onEach { if (it.size == 3) ready.complete(Unit) }.first { it.size == 1 }
        } }
        ready.await()
        val ids = setOf(first, second)
        val preview = contacts.previewDelete(ids)
        assertEquals(ContactDeletePreview(mapOf(first to 0, second to 0)), preview)
        assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(2, 0)), contacts.deleteContacts(ids, preview))
        assertEquals(keep, refreshed.await().single().id)
    }

    @Test fun 准确统计关联联系人和礼金并通过外键级联删除() = runBlocking {
        val first = contact("级联甲")
        val second = contact("级联乙")
        val empty = contact("无记录测试")
        val keep = contact("保留测试")
        gift(first); gift(first); gift(second)
        val keepGift = gift(keep)
        val ids = setOf(first, second, empty)
        val preview = contacts.previewDelete(ids)
        assertEquals(3, preview.contactCount)
        assertEquals(2, preview.contactsWithGiftRecords)
        assertEquals(3, preview.giftRecordCount)
        assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(3, 3)), contacts.deleteContacts(ids, preview))
        assertEquals(keep, contacts.observeContacts().first().single().id)
        assertEquals(keepGift, gifts.observeAll().first().single().record.id)
    }

    @Test fun 空集合及不存在ID绝不删除其他数据() = runBlocking {
        val keep = contact("保留测试")
        gift(keep)
        for (ids in listOf(emptySet(), setOf(-1L, 0L, 9999L))) {
            val preview = contacts.previewDelete(ids)
            assertEquals(0, preview.contactCount)
            assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(0, 0)), contacts.deleteContacts(ids, preview))
        }
        assertEquals(1, contacts.observeContacts().first().size)
        assertEquals(1, gifts.observeAll().first().size)
    }

    @Test fun 确认后新增礼金必须重新确认且旧确认不会执行删除() = runBlocking {
        val id = contact("复查测试")
        val ids = setOf(id)
        val preview = contacts.previewDelete(ids)
        gift(id)
        val changed = contacts.deleteContacts(ids, preview) as ContactBulkDeleteOutcome.Changed
        assertEquals(1, changed.preview.giftRecordCount)
        assertEquals(1, contacts.observeContacts().first().size)
        assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(1, 1)), contacts.deleteContacts(ids, changed.preview))
    }

    @Test fun 联系人数量减少也要求重新确认并返回实际删除数量() = runBlocking {
        val first = contact("变化甲")
        val second = contact("变化乙")
        val ids = setOf(first, second)
        val preview = contacts.previewDelete(ids)
        contacts.delete(contacts.observeContact(first).first()!!)
        val changed = contacts.deleteContacts(ids, preview) as ContactBulkDeleteOutcome.Changed
        assertEquals(1, changed.preview.contactCount)
        assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(1, 0)), contacts.deleteContacts(ids, changed.preview))
    }

    @Test fun 导入上千联系人后分片删除保持原子性及兼容性() = runBlocking {
        contacts.importDeviceContacts((0 until 1005).map {
            ContactImportSelection(DeviceContact(it.toLong(), "导入测试$it", "+1202555${it.toString().padStart(4, '0')}"), true)
        })
        val ids = contacts.observeContacts().first().map { it.id }.toSet()
        gift(ids.first())
        val preview = contacts.previewDelete(ids)
        assertEquals(1005, preview.contactCount)
        assertEquals(ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(1005, 1)), contacts.deleteContacts(ids, preview))
        assertTrue(contacts.observeContacts().first().isEmpty())
        assertTrue(gifts.observeAll().first().isEmpty())
    }

    @Test fun 后续分片失败时联系人和级联礼金全部回滚() = runBlocking {
        database.contactDao().insertAll((1..901).map { com.yangsong.lizhang.data.local.entity.ContactEntity(id = it.toLong(), name = "回滚测试$it") })
        gift(1)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER test_delete_abort BEFORE DELETE ON contacts WHEN OLD.id = 901 BEGIN SELECT RAISE(ABORT, '测试回滚'); END")
        val ids = (1L..901L).toSet()
        val preview = contacts.previewDelete(ids)
        assertTrue(runCatching { contacts.deleteContacts(ids, preview) }.isFailure)
        assertEquals(901, contacts.observeContacts().first().size)
        assertEquals(1, gifts.observeAll().first().size)
    }
}
