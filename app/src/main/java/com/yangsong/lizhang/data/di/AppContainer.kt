package com.yangsong.lizhang.data.di

import android.content.Context
import androidx.room.Room
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.contact.DeviceContactDataSource
import com.yangsong.lizhang.data.preferences.SharedPreferencesThemeRepository
import com.yangsong.lizhang.data.reminder.AndroidReminderRepository
import com.yangsong.lizhang.data.reminder.ReminderCoordinator
import com.yangsong.lizhang.data.repository.RoomBackupRepository
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.DeviceContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.ReminderRepository
import com.yangsong.lizhang.domain.repository.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 应用级依赖组合根，避免在界面层直接创建数据库或仓库。 */
class AppContainer(
    context: Context,
    val deviceContactRepository: DeviceContactRepository =
        DeviceContactDataSource(context.applicationContext.contentResolver),
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: LiZhangDatabase = Room.databaseBuilder(
        context.applicationContext,
        LiZhangDatabase::class.java,
        DatabaseConstants.NAME,
    ).build()

    val contactRepository: ContactRepository = RoomContactRepository(database.contactDao())
    val giftRecordRepository: GiftRecordRepository = RoomGiftRecordRepository(database.giftRecordDao())
    val backupRepository: BackupRepository = RoomBackupRepository(database)
    val reminderRepository: ReminderRepository = AndroidReminderRepository(context.applicationContext)
    val themeRepository: ThemeRepository = SharedPreferencesThemeRepository(context.applicationContext)
    private val reminderCoordinator = ReminderCoordinator(
        giftRecordRepository,
        reminderRepository,
        applicationScope,
    )

    fun startReminderCoordination() = reminderCoordinator.start()
    fun refreshReminderSchedules() = reminderCoordinator.refresh()
}
