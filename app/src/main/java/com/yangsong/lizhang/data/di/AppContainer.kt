package com.yangsong.lizhang.data.di

import android.content.Context
import androidx.room.Room
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository

/** 应用级依赖组合根，避免在界面层直接创建数据库或仓库。 */
class AppContainer(context: Context) {
    private val database: LiZhangDatabase = Room.databaseBuilder(
        context.applicationContext,
        LiZhangDatabase::class.java,
        DatabaseConstants.NAME,
    ).build()

    val contactRepository: ContactRepository = RoomContactRepository(database.contactDao())
    val giftRecordRepository: GiftRecordRepository = RoomGiftRecordRepository(database.giftRecordDao())
}
