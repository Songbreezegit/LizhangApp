package com.yangsong.lizhang.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLedgerRepositoryInstrumentedTest {
    private lateinit var database: LiZhangDatabase
    private lateinit var contactRepository: RoomContactRepository
    private lateinit var giftRepository: RoomGiftRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiZhangDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contactRepository = RoomContactRepository(database.contactDao())
        giftRepository = RoomGiftRecordRepository(database.giftRecordDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 搜索覆盖姓名手机号和礼金备注且联系人按最近往来汇总() = runBlocking {
        val olderContactId = contactRepository.create(
            Contact(
                name = "张三",
                phone = "13800000001",
                relationship = "同学",
                notes = "老家亲友",
                createdTime = 100,
            ),
        )
        val newerContactId = contactRepository.create(
            Contact(
                name = "李四",
                phone = "13900000002",
                relationship = "同事",
                createdTime = 200,
            ),
        )
        giftRepository.create(
            gift(
                contactId = olderContactId,
                amount = 60_000,
                direction = GiftDirection.RECEIVED,
                eventDate = 1_000,
                notes = "婚礼祝福备注",
            ),
        )
        giftRepository.create(
            gift(
                contactId = olderContactId,
                amount = 20_000,
                direction = GiftDirection.GIVEN,
                eventDate = 2_000,
            ),
        )
        giftRepository.create(
            gift(
                contactId = newerContactId,
                amount = 30_000,
                direction = GiftDirection.RECEIVED,
                eventDate = 3_000,
            ),
        )

        assertEquals(2, giftRepository.observeSearch("张三").first().size)
        assertEquals(2, giftRepository.observeSearch("13800000001").first().size)
        assertEquals(
            listOf(60_000L),
            giftRepository.observeSearch("祝福备注").first().map { it.record.amountInCents },
        )

        val summaries = contactRepository.observeContactSummaries().first()
        assertEquals(listOf("李四", "张三"), summaries.map { it.contact.name })
        val olderSummary = summaries.single { it.contact.id == olderContactId }
        assertEquals(60_000L, olderSummary.receivedInCents)
        assertEquals(20_000L, olderSummary.givenInCents)
        assertEquals(2_000L, olderSummary.lastInteractionTime)
        assertEquals(
            listOf("张三"),
            contactRepository.observeContactSummaries("老家亲友").first().map { it.contact.name },
        )
    }

    @Test
    fun 联系人与礼金支持完整增改删且删除联系人级联清理记录() = runBlocking {
        val contactId = contactRepository.create(Contact(name = "待编辑联系人"))
        val recordId = giftRepository.create(
            gift(
                contactId = contactId,
                amount = 10_000,
                direction = GiftDirection.RECEIVED,
                eventDate = 1_000,
            ),
        )

        val contact = contactRepository.observeContact(contactId).first()
        requireNotNull(contact)
        contactRepository.update(contact.copy(name = "已编辑联系人", phone = "13700000000"))
        val record = giftRepository.observeRecord(recordId).first()
        requireNotNull(record)
        giftRepository.update(record.copy(amountInCents = 88_800, notes = "已编辑备注"))

        assertEquals("已编辑联系人", contactRepository.observeContact(contactId).first()?.name)
        assertEquals(88_800L, giftRepository.observeRecord(recordId).first()?.amountInCents)

        contactRepository.delete(contactRepository.observeContact(contactId).first()!!)

        assertNull(contactRepository.observeContact(contactId).first())
        assertNull(giftRepository.observeRecord(recordId).first())
        assertEquals(emptyList<GiftRecord>(), database.giftRecordDao().getAllForBackup())
    }

    @Test
    fun 首页年度汇总和日历范围查询不读取无关年份记录() = runBlocking {
        val contactId = contactRepository.create(Contact(name = "年度测试联系人"))
        val jan2026 = date(2026, Calendar.JANUARY, 12)
        val feb2026 = date(2026, Calendar.FEBRUARY, 1)
        giftRepository.create(gift(contactId, 10_000, GiftDirection.RECEIVED, jan2026))
        giftRepository.create(gift(contactId, 5_000, GiftDirection.GIVEN, feb2026))

        val summary = giftRepository.observeYearlySummaries().first().single()
        assertEquals(2026, summary.year)
        assertEquals(10_000L, summary.receivedInCents)
        assertEquals(5_000L, summary.givenInCents)
        assertEquals(
            listOf(jan2026),
            giftRepository.observeBetween(date(2026, Calendar.JANUARY, 1), date(2026, Calendar.FEBRUARY, 1))
                .first()
                .map { it.record.eventDate },
        )
    }

    private fun date(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis

    private fun gift(
        contactId: Long,
        amount: Long,
        direction: GiftDirection,
        eventDate: Long,
        notes: String? = null,
    ) = GiftRecord(
        contactId = contactId,
        amountInCents = amount,
        eventType = EventType.WEDDING,
        eventDate = eventDate,
        direction = direction,
        notes = notes,
    )
}
