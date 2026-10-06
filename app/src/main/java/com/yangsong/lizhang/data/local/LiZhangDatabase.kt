package com.yangsong.lizhang.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yangsong.lizhang.data.local.dao.ContactDao
import com.yangsong.lizhang.data.local.dao.GiftRecordDao
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity

const val LIZHANG_DATABASE_VERSION = 2

@Database(
    entities = [ContactEntity::class, GiftRecordEntity::class],
    version = LIZHANG_DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class LiZhangDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun giftRecordDao(): GiftRecordDao
}

/** 仅增加可空字段，保留所有联系人和礼金记录。 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE gift_records ADD COLUMN customEventName TEXT DEFAULT NULL")
    }
}
