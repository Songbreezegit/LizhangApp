package com.yangsong.lizhang.flow

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.ui.screen.ContactsScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactBulkManagementFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LiZhangDatabase
    private lateinit var repository: RoomContactRepository
    private lateinit var gifts: RoomGiftRecordRepository
    private lateinit var vm: ContactsViewModel
    private val store = ViewModelStore()
    private val shown = mutableStateOf(true)
    private var clickedId: Long? = null
    private var firstId = 0L
    private var secondId = 0L

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LiZhangDatabase::class.java).build()
        repository = RoomContactRepository(database.contactDao())
        gifts = RoomGiftRecordRepository(database.giftRecordDao())
        firstId = repository.create(Contact(name = "甲批量测试", phone = "13800000000"))
        secondId = repository.create(Contact(name = "乙批量测试", phone = "13900000000"))
        vm = ViewModelProvider(store, ContactsViewModel.factory(repository))[ContactsViewModel::class.java]
    }
    @After fun teardown() {
        compose.runOnIdle { shown.value = false; store.clear() }
        compose.waitForIdle()
        database.close()
    }
    private fun start() {
        compose.setContent { if (shown.value) LiZhangTheme {
            ContactsScreen(vm, { clickedId = it }, {}, {})
        } }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading }
    }
    private fun chooseFirst() = compose.onNodeWithText("甲批量测试").performScrollTo().performClick()
    private fun chooseSecond() = compose.onNodeWithText("乙批量测试").performScrollTo().performClick()
    private fun addGift() = runBlocking {
        gifts.create(GiftRecord(contactId = firstId, amountInCents = 100, eventType = EventType.OTHER,
            eventDate = 1_700_000_000_000, direction = GiftDirection.GIVEN))
    }
    private fun waitForMessage(message: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun 管理入口多选数量取消全选及返回键恢复详情点击() {
        start()
        compose.onNodeWithTag("联系人添加菜单").assertExists()
        compose.onNodeWithText("管理").performClick()
        compose.onNodeWithText("已选择 0 人").assertExists()
        compose.onNodeWithText("删除 0 位联系人").assertIsNotEnabled()
        chooseFirst(); chooseSecond()
        compose.onNodeWithText("已选择 2 人").assertExists()
        compose.onNodeWithText("删除 2 位联系人").assertIsEnabled()
        assertNull(clickedId)
        compose.onNodeWithText("取消全选").performClick()
        compose.onNodeWithText("已选择 0 人").assertExists()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选择 2 人").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) { !vm.uiState.value.isSelectionMode }
        compose.onNodeWithText("管理").assertExists()
        chooseFirst()
        assertEquals(firstId, clickedId)
    }

    @Test fun 无记录确认可取消再次确认后退出管理并刷新() {
        start()
        compose.onNodeWithText("管理").performClick()
        chooseFirst()
        compose.onNodeWithText("删除 1 位联系人").performClick()
        waitForMessage("删除 1 位联系人？")
        compose.onNodeWithText("删除后无法恢复。").assertExists()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("已选择 1 人").assertExists()
        assertEquals(2, runBlocking { repository.observeContacts().first().size })
        compose.onNodeWithText("删除 1 位联系人").performClick()
        waitForMessage("删除 1 位联系人？")
        compose.onNodeWithText("删除").performClick()
        waitForMessage("已删除 1 位联系人")
        compose.onNodeWithText("管理").assertExists()
        compose.onNodeWithText("甲批量测试").assertDoesNotExist()
        assertEquals(secondId, runBlocking { repository.observeContacts().first().single().id })
    }

    @Test fun 礼金高风险提示必须勾选并确认后才级联删除() {
        addGift(); addGift()
        start()
        compose.onNodeWithText("管理").performClick()
        chooseFirst()
        compose.onNodeWithText("删除 1 位联系人").performClick()
        waitForMessage("删除联系人及记录")
        compose.onNodeWithText("其中 1 位联系人存在礼金往来记录。\n继续删除将同时永久删除 2 条礼金记录。\n\n此操作无法恢复。").assertExists()
        compose.onNodeWithText("删除联系人及记录").assertIsNotEnabled()
        compose.onNodeWithText("我确认同时永久删除 2 条礼金记录").performClick()
        compose.onNodeWithText("删除联系人及记录").assertIsEnabled().performClick()
        waitForMessage("已删除 1 位联系人及 2 条礼金记录")
        assertTrue(runBlocking { gifts.observeAll().first().isEmpty() })
        assertEquals(secondId, runBlocking { repository.observeContacts().first().single().id })
    }

    @Test fun 确认期间新增礼金必须显示新数量并再次确认() {
        start()
        compose.onNodeWithText("管理").performClick()
        chooseFirst()
        compose.onNodeWithText("删除 1 位联系人").performClick()
        waitForMessage("删除 1 位联系人？")
        addGift()
        compose.onNodeWithText("删除").performClick()
        waitForMessage("数据已发生变化，请核对最新数量并重新确认。")
        compose.onNodeWithText("我确认同时永久删除 1 条礼金记录").assertExists()
        compose.onNodeWithText("删除联系人及记录").assertIsNotEnabled()
        assertEquals(2, runBlocking { repository.observeContacts().first().size })
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("已选择 1 人").assertExists()
    }
}
