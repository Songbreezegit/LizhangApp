package com.yangsong.lizhang.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.GiftSaveTestActivity
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.ui.screen.AddGiftScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.GiftEditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 在通讯录、通知权限均拒绝的测试安装中，使用合成数据验证真实记账页面与 Room。 */
class PermissionDeniedLedgerInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<GiftSaveTestActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: LiZhangDatabase

    @Before fun prepare() {
        assertOptionalPermissionsDenied()
        database = Room.inMemoryDatabaseBuilder(context, LiZhangDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        if (::database.isInitialized) database.close()
    }

    @Test fun 拒绝通讯录与通知仍可手动新增修改查询删除礼金() {
        val contacts = RoomContactRepository(database.contactDao())
        val records = RoomGiftRecordRepository(database.giftRecordDao())
        val contactId = runBlocking { contacts.create(Contact(name = "权限拒绝合成联系人")) }
        lateinit var editor: GiftEditorViewModel
        compose.runOnUiThread {
            editor = ViewModelProvider(compose.activity,
                GiftEditorViewModel.factory(records, contacts, initialContactId = contactId))[GiftEditorViewModel::class.java]
            editor.update { it.copy(amount = "12.34", notes = "权限拒绝合成记录") }
        }
        compose.setContent { LiZhangTheme { AddGiftScreen(editor, onBack = {}) } }
        compose.onNodeWithTag("礼金保存栏").assertIsEnabled().performClick()
        compose.waitUntil(5000) { editor.uiState.value.isSaved }
        runBlocking {
            val saved = records.observeAll().first().single().record
            assertEquals(contactId, saved.contactId)
            assertEquals(1234L, saved.amountInCents)
            records.update(saved.copy(amountInCents = 2345L))
            assertEquals(2345L, records.observeRecord(saved.id).first()!!.amountInCents)
            records.delete(saved)
            assertTrue(records.observeAll().first().isEmpty())
            assertEquals(1, contacts.observeContacts().first().size)
        }
        assertOptionalPermissionsDenied()
    }

    private fun assertOptionalPermissionsDenied() {
        assertEquals("测试安装必须先拒绝通讯录权限", PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS))
        if (Build.VERSION.SDK_INT >= 33) assertEquals("测试安装必须先拒绝通知权限", PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
    }
}
