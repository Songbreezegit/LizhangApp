package com.yangsong.lizhang.data.repository

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.data.local.LIZHANG_DATABASE_VERSION
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity
import com.yangsong.lizhang.domain.backup.InvalidBackupException
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackupRepositoryInstrumentedTest {
    private lateinit var database: LiZhangDatabase
    private lateinit var repository: RoomBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LiZhangDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomBackupRepository(database) { BACKUP_TIME }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 创建备份后可在当前数据库完整替换恢复() = runBlocking {
        database.contactDao().insert(CONTACT)
        database.giftRecordDao().insert(RECORD)
        val backup = repository.createBackup()

        database.giftRecordDao().deleteAll()
        database.contactDao().deleteAll()
        database.contactDao().insert(CONTACT.copy(id = 99, name = "待替换数据"))

        val summary = repository.restoreBackup(backup.bytes)

        assertEquals(BACKUP_TIME, summary.createdTime)
        assertEquals(LIZHANG_DATABASE_VERSION, summary.sourceDatabaseVersion)
        assertEquals(listOf(CONTACT), database.contactDao().getAllForBackup())
        assertEquals(listOf(RECORD), database.giftRecordDao().getAllForBackup())
    }

    @Test
    fun 损坏备份在事务开始前失败且当前数据保持不变() = runBlocking {
        database.contactDao().insert(CONTACT)
        database.giftRecordDao().insert(RECORD)
        val corrupted = repository.createBackup().bytes.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }

        val error = runCatching { repository.restoreBackup(corrupted) }.exceptionOrNull()

        assertTrue(error is InvalidBackupException)
        assertEquals(listOf(CONTACT), database.contactDao().getAllForBackup())
        assertEquals(listOf(RECORD), database.giftRecordDao().getAllForBackup())
    }

    @Test
    fun 旧版格式一备份可恢复到当前Room数据库() = runBlocking {
        val summary = repository.restoreBackup(Base64.decode(LEGACY_V1_BACKUP_BASE64, Base64.DEFAULT))

        assertEquals(1, summary.sourceDatabaseVersion)
        assertEquals(1, summary.contactCount)
        assertEquals(1, summary.giftRecordCount)
        assertEquals("Legacy User", database.contactDao().getAllForBackup().single().name)
        assertEquals(88_800, database.giftRecordDao().getAllForBackup().single().amountInCents)
    }

    private companion object {
        const val BACKUP_TIME = 1_754_000_000_000L
        const val LEGACY_V1_BACKUP_BASE64 =
            "TElaSEFORy1CQUNLVVAKAAAAkwAAAAEAAAGYYowEAAAAAAEAAAAAAAAACAAAAAtMZWdhY3kgVXNlcv////8AAAAGRmFtaWx5/////wAAAYvP5WgAAAAAAQAAAAAAAAAMAAAAAAAAAAgAAAAAAAFa4AAAAAdXRURESU5HAAABmBpCKAAAAAAIUkVDRUlWRUQAAAALTGVnYWN5IGdpZnQAAAGYGkIoAI0mSxh9zDfLQZ7vG11SHmT4cmR8bHI9UQ9+K4JTQyDE"

        val CONTACT = ContactEntity(
            id = 8,
            name = "测试联系人",
            phone = "13800000000",
            relationship = "亲友",
            notes = "兼容恢复测试",
            createdTime = 1_700_000_000_000,
        )
        val RECORD = GiftRecordEntity(
            id = 12,
            contactId = CONTACT.id,
            amountInCents = 88_800,
            eventType = EventType.WEDDING,
            eventDate = 1_752_787_200_000,
            direction = GiftDirection.RECEIVED,
            notes = "测试礼金",
            createdTime = 1_752_787_200_000,
        )
    }
}
