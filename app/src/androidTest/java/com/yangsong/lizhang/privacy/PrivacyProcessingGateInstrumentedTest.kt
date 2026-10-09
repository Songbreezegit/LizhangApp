package com.yangsong.lizhang.privacy

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.local.entity.ContactEntity
import com.yangsong.lizhang.data.preferences.SharedPreferencesPrivacyConsentRepository
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.fixtures.loadedConsentDocuments
import com.yangsong.lizhang.domain.repository.DeviceContactRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 使用独立文件名和合成记录验证门控；不删除或记录正式账本。 */
class PrivacyProcessingGateInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("rc2_gate_test_$name", mode)
        override fun getDatabasePath(name: String) = base.getDatabasePath("rc2_gate_test_$name")
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            base.openOrCreateDatabase(if (name == DatabaseConstants.NAME) "rc2_gate_test_$name" else name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            base.openOrCreateDatabase(if (name == DatabaseConstants.NAME) "rc2_gate_test_$name" else name, mode, factory, errorHandler)
    }
    private val preferences get() = context.getSharedPreferences(SharedPreferencesPrivacyConsentRepository.FILE_NAME, Context.MODE_PRIVATE)
    @Before fun 准备() {
        preferences.edit().clear().commit()
        base.deleteDatabase("rc2_gate_test_${DatabaseConstants.NAME}")
    }
    @After fun 收尾() {
        preferences.edit().clear().commit()
        base.deleteDatabase("rc2_gate_test_${DatabaseConstants.NAME}")
    }

    @Test fun 未确认和拒绝时离线文档可读但数据库和提醒不初始化() = runBlocking {
        var reads = 0
        val container = AppContainer(context, DeviceContactRepository { reads++; emptyList() })
        assertFalse(container.canProcessPersonalData)
        container.startReminderCoordination()
        container.refreshReminderSchedules()
        assertFalse(container.isBusinessDatabaseInitialized)
        assertFalse(container.isReminderRepositoryInitialized)
        assertTrue(container.legalDocumentRepository.load(LegalDocumentType.PRIVACY).sections.isNotEmpty())
        assertTrue(container.privacyConsentRepository.declineCurrentPolicy())
        container.startReminderCoordination()
        assertTrue(runCatching { container.contactRepository }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { container.deviceContactRepository.readContacts() }.exceptionOrNull() is IllegalStateException)
        assertFalse(container.isBusinessDatabaseInitialized)
        assertFalse(container.isReminderRepositoryInitialized)
        assertFalse(File(context.getDatabasePath(DatabaseConstants.NAME).path).exists())
        assertEquals(0, reads)
    }

    @Test fun 升级已有账本在拒绝和确认前后原样保留() = runBlocking {
        val database = Room.databaseBuilder(context, LiZhangDatabase::class.java, DatabaseConstants.NAME).build()
        database.contactDao().insert(ContactEntity(name = "隐私升级合成样本", createdTime = 1L))
        val before = database.contactDao().getAllForBackup()
        val container = AppContainer(context, DeviceContactRepository { emptyList() })
        assertTrue(container.onboardingRepository.state.value.completed)
        assertFalse(container.canProcessPersonalData)
        assertFalse(container.isBusinessDatabaseInitialized)
        container.privacyConsentRepository.declineCurrentPolicy()
        assertEquals(before, database.contactDao().getAllForBackup())
        assertTrue(container.privacyConsentRepository.acceptCurrentPolicy(
            loadedConsentDocuments(container.legalDocumentRepository)))
        assertEquals(before, database.contactDao().getAllForBackup())
        assertFalse(container.isBusinessDatabaseInitialized)
        database.close()
    }
}
