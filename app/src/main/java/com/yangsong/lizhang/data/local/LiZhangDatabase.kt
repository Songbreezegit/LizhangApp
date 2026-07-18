package com.yangsong.lizhang.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yangsong.lizhang.data.local.dao.ContactDao
import com.yangsong.lizhang.data.local.dao.GiftRecordDao
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.local.entity.GiftRecordEntity

@Database(
    entities = [ContactEntity::class, GiftRecordEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class LiZhangDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun giftRecordDao(): GiftRecordDao
}
