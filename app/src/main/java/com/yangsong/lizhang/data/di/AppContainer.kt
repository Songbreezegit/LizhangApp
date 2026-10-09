package com.yangsong.lizhang.data.di

import android.content.Context
import androidx.room.Room
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.contact.DeviceContactDataSource
import com.yangsong.lizhang.data.preferences.SharedPreferencesThemeRepository
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.data.preferences.SharedPreferencesPrivacyConsentRepository
import com.yangsong.lizhang.data.legal.AssetLegalDocumentRepository
import com.yangsong.lizhang.domain.repository.OnboardingRepository
import com.yangsong.lizhang.domain.repository.PrivacyConsentRepository
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
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
    deviceContactRepository: DeviceContactRepository =
        DeviceContactDataSource(context.applicationContext.contentResolver),
) {
    // 先捕获旧安装痕迹，再初始化可能创建本地文件的业务仓库。
    val onboardingRepository: OnboardingRepository = SharedPreferencesOnboardingRepository(context.applicationContext)
    val privacyConsentRepository: PrivacyConsentRepository = SharedPreferencesPrivacyConsentRepository(context.applicationContext)
    val legalDocumentRepository: LegalDocumentRepository = AssetLegalDocumentRepository(context.applicationContext)
    val canProcessPersonalData: Boolean get() = privacyConsentRepository.state.value.canProcessPersonalData
    private val deviceContactSource = deviceContactRepository
    val deviceContactRepository: DeviceContactRepository = DeviceContactRepository {
        check(canProcessPersonalData) { "尚未确认当前隐私告知，不能读取设备通讯录" }
        deviceContactSource.readContacts()
    }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val databaseDelegate = lazy {
        check(canProcessPersonalData) { "尚未确认当前隐私告知，不能初始化账本" }
        Room.databaseBuilder(
            context.applicationContext,
            LiZhangDatabase::class.java,
            DatabaseConstants.NAME,
        ).addMigrations(com.yangsong.lizhang.data.local.MIGRATION_1_2).build()
    }
    private val database: LiZhangDatabase by databaseDelegate
    internal val isBusinessDatabaseInitialized: Boolean get() = databaseDelegate.isInitialized()

    val contactRepository: ContactRepository by lazy { RoomContactRepository(database.contactDao()) }
    val giftRecordRepository: GiftRecordRepository by lazy { RoomGiftRecordRepository(database.giftRecordDao()) }
    val backupRepository: BackupRepository by lazy { RoomBackupRepository(database) }
    private val reminderDelegate = lazy {
        check(canProcessPersonalData) { "尚未确认当前隐私告知，不能读取提醒" }
        AndroidReminderRepository(context.applicationContext)
    }
    val reminderRepository: ReminderRepository by reminderDelegate
    internal val isReminderRepositoryInitialized: Boolean get() = reminderDelegate.isInitialized()
    val themeRepository: ThemeRepository = SharedPreferencesThemeRepository(context.applicationContext)
    private val reminderCoordinator by lazy { ReminderCoordinator(
        reminderRepository,
        applicationScope,
    ) }

    fun startReminderCoordination() {
        if (canProcessPersonalData) reminderCoordinator.start()
    }
    fun refreshReminderSchedules() {
        if (canProcessPersonalData) reminderCoordinator.refresh()
    }
}
