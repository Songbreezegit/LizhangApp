package com.yangsong.lizhang.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.local.MIGRATION_1_2
import com.yangsong.lizhang.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomEventMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LiZhangDatabase::class.java)

    @Test fun 版本一迁移保留全部旧数据且新字段为空() = runBlocking {
        val name = "migration-custom-event-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO contacts(id,name,phone,relationship,notes,createdTime) VALUES(1,'迁移测试联系人',NULL,'朋友','旧备注',1000)")
            execSQL("INSERT INTO gift_records(id,contactId,amountInCents,eventType,eventDate,direction,notes,createdTime) VALUES(2,1,12345,'BIRTHDAY',1000,'RECEIVED','旧记录',1000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, LiZhangDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val contact = db.contactDao().getAllForBackup().single()
            assertEquals("迁移测试联系人", contact.name)
            assertEquals("旧备注", contact.notes)
            val old = db.giftRecordDao().getAllForBackup().single()
            assertEquals(12345L, old.amountInCents)
            assertEquals("旧记录", old.notes)
            assertEquals(EventType.BIRTHDAY, old.eventType)
            assertNull(old.customEventName)
            val repository = RoomGiftRecordRepository(db.giftRecordDao())
            val custom = GiftRecord(contactId = 1, amountInCents = 20000, eventType = EventType.OTHER,
                eventDate = 1000, direction = GiftDirection.GIVEN, customEventName = "升学宴")
            val id = repository.create(custom)
            assertEquals("升学宴", repository.observeRecord(id).first()?.customEventName)
            val backup = RoomBackupRepository(db).createBackup()
            db.giftRecordDao().deleteAll()
            RoomBackupRepository(db).restoreBackup(backup.bytes)
            assertEquals("升学宴", repository.observeRecord(id).first()?.customEventName)
            assertEquals(2, repository.observeAll().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
