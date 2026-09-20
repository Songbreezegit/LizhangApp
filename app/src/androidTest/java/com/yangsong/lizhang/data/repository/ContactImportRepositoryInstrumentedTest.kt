package com.yangsong.lizhang.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactImportResult
import com.yangsong.lizhang.domain.model.ContactImportSelection
import com.yangsong.lizhang.domain.model.DeviceContact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.export.GiftRecordXlsxFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactImportRepositoryInstrumentedTest {
    private lateinit var database: LiZhangDatabase
    private lateinit var repository: RoomContactRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LiZhangDatabase::class.java).build()
        repository = RoomContactRepository(database.contactDao())
    }
    @After fun teardown() = database.close()

    @Test fun 三类结果统计且同名手选只新增不覆盖原联系人() = runBlocking {
        repository.create(Contact(name = "原有同号", phone = "+86 13800000000"))
        repository.create(Contact(name = "同名有号码", phone = "13900000000"))
        repository.create(Contact(name = "同名无号码", phone = null))
        val originals = repository.observeContacts().first()
        val selections = listOf(
            ContactImportSelection(DeviceContact(1, "另一姓名", "138-0000-0000"), true, true),
            ContactImportSelection(DeviceContact(2, "同名有号码", "13700000000"), false),
            ContactImportSelection(DeviceContact(3, "同名无号码", "13600000000"), true, true),
            ContactImportSelection(DeviceContact(4, "全新测试", "+12025550123"), true),
        )
        assertEquals(ContactImportResult(2, 1, 1), repository.importDeviceContacts(selections))
        originals.forEach { assertEquals(it, repository.observeContact(it.id).first()) }
        assertEquals(5, repository.observeContacts().first().size)
        assertEquals(ContactImportResult(0, 3, 1), repository.importDeviceContacts(selections))
    }

    @Test fun 正式提交按最新数据库复查同号及新增同名冲突() = runBlocking {
        val selections = listOf(
            ContactImportSelection(DeviceContact(1, "原本新增甲", "13800000000"), true),
            ContactImportSelection(DeviceContact(2, "原本新增乙", "13900000000"), true),
        )
        repository.create(Contact(name = "其他名字", phone = "+86 13800000000"))
        repository.create(Contact(name = "原本新增乙", phone = null))
        assertEquals(ContactImportResult(0, 1, 1), repository.importDeviceContacts(selections))
        assertEquals(2, repository.observeContacts().first().size)
    }

    @Test fun 完整候选按标准化号码去重且并发导入保持幂等() = runBlocking {
        val selections = listOf(
            ContactImportSelection(DeviceContact(1, "测试甲", "+86 13800000000"), true),
            ContactImportSelection(DeviceContact(2, "测试重复", "138-0000-0000"), true),
        )
        val results = awaitAll(async { repository.importDeviceContacts(selections) }, async { repository.importDeviceContacts(selections) })
        assertEquals(1, results.sumOf { it.imported })
        assertEquals(1, results.sumOf { it.skipped })
        assertEquals(1, repository.observeContacts().first().size)
    }

    @Test fun 批量持久化并通知正在观察的列表且重复执行幂等() = runBlocking {
        val firstValue = CompletableDeferred<Unit>()
        val refreshed = async {
            withTimeout(10_000) {
                repository.observeContacts().onEach { if (it.isEmpty()) firstValue.complete(Unit) }.first { it.size == 500 }
            }
        }
        firstValue.await()
        val contacts = (0 until 500).map { Contact(name = "虚构联系人$it", phone = "+1202555${it.toString().padStart(4, '0')}") }
        assertEquals(ContactImportResult(500, 0), repository.createAll(contacts))
        assertEquals(500, refreshed.await().size)
        assertEquals(ContactImportResult(0, 500), repository.createAll(contacts))
        assertEquals("+12025550001", repository.observeContacts("虚构联系人1").first().first { it.name == "虚构联系人1" }.phone)
    }

    @Test fun 写入前复查并对同批次与并行导入去重() = runBlocking {
        repository.create(Contact(name = "既有测试", phone = "+86 13800000000"))
        val batch = listOf(Contact(name = "同号异名", phone = "138-0000-0000"), Contact(name = "新增测试", phone = "+1 202 555 0123"))
        val results = awaitAll(async { repository.createAll(batch + batch) }, async { repository.createAll(batch) })
        assertEquals(1, results.sumOf { it.imported })
        assertEquals(5, results.sumOf { it.skipped })
        assertEquals(2, repository.observeContacts().first().size)
    }

    @Test fun 插入失败整批回滚且原联系人可继续增删改() = runBlocking {
        val id = repository.create(Contact(name = "原有测试"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER test_import_abort BEFORE INSERT ON contacts WHEN NEW.name = '中止测试' BEGIN SELECT RAISE(ABORT, '测试回滚'); END")
        try {
            repository.importDeviceContacts(listOf(
                ContactImportSelection(DeviceContact(1, "第一条测试", "13800000000"), true),
                ContactImportSelection(DeviceContact(2, "中止测试", "13900000000"), true),
            ))
            fail("应回滚批量事务")
        } catch (_: android.database.sqlite.SQLiteException) {
            assertEquals(1, repository.observeContacts().first().size)
        }
        val contact = repository.observeContact(id).first()!!
        repository.update(contact.copy(name = "已修改测试"))
        assertEquals("已修改测试", repository.observeContact(id).first()!!.name)
        repository.delete(contact)
        assertNull(repository.observeContact(id).first())
    }

    @Test fun 导入联系人可关联礼金并兼容备份恢复与导出() = runBlocking {
        repository.importDeviceContacts(listOf(ContactImportSelection(DeviceContact(1, "备份导入测试", "+12025550123"), true)))
        val contact = repository.observeContacts().first().single()
        val gifts = RoomGiftRecordRepository(database.giftRecordDao())
        val giftId = gifts.create(GiftRecord(contactId = contact.id, amountInCents = 100, eventType = EventType.WEDDING,
            eventDate = 1_700_000_000_000, direction = GiftDirection.GIVEN))
        repository.createAll(listOf(contact.copy(name = "重复测试")))
        assertEquals(contact.id, gifts.observeSearch("").first().single().record.contactId)
        val backupRepository = RoomBackupRepository(database)
        val backup = backupRepository.createBackup()
        database.giftRecordDao().deleteAll()
        database.contactDao().deleteAll()
        backupRepository.restoreBackup(backup.bytes)
        assertEquals(contact, repository.observeContact(contact.id).first())
        val rows = gifts.observeSearch("备份导入测试").first()
        assertEquals(giftId, rows.single().record.id)
        assertTrue(GiftRecordCsvFormatter.format(rows).contains("备份导入测试"))
        assertTrue(GiftRecordXlsxFormatter.format(rows).isNotEmpty())
        assertEquals(1, repository.observeContactSummaries().first().size)
    }
}
