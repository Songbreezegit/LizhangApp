package com.yangsong.lizhang.flow

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.SideEffect

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.ui.screen.AddGiftScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.GiftEditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class GiftSaveFlowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: LiZhangDatabase
    private lateinit var contacts: RoomContactRepository
    private lateinit var records: RoomGiftRecordRepository
    private lateinit var repository: ControlledRepository
    private lateinit var viewModel: GiftEditorViewModel
    private var contactId = 0L
    private val returns = AtomicInteger()
    private val returnedAt = AtomicLong()
    private val imeBottom = AtomicInteger()
    private val restore by lazy { StateRestorationTester(compose) }

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LiZhangDatabase::class.java)
            .allowMainThreadQueries().build()
        contacts = RoomContactRepository(database.contactDao())
        records = RoomGiftRecordRepository(database.giftRecordDao())
        repository = ControlledRepository(records)
        contactId = runBlocking { contacts.create(Contact(name = "保存测试联系人")) }
    }
    @After fun close() { database.close() }

    private fun start(dark: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            viewModel = GiftEditorViewModel(repository, contacts, initialContactId = contactId)
            viewModel.update { it.copy(amount = "100", eventDate = 1789862400000L,
                direction = GiftDirection.GIVEN, eventType = EventType.BIRTHDAY, notes = "保存测试备注") }
        }
        restore.setContent { LiZhangTheme(dark) {
            val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom.set(bottom) }
            AddGiftScreen(viewModel) { returnedAt.set(SystemClock.uptimeMillis()); returns.incrementAndGet() }
        } }
        compose.onNodeWithTag("礼金保存栏").assertIsEnabled()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "gift-save-review").apply { mkdirs() }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(directory, "$name.png")))
    }

    @Test fun 保存栏尺寸不跳动且成功约450毫秒只返回一次() {
        repository.gate = CompletableDeferred()
        start()
        val before = compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.onNodeWithTag("礼金保存栏").assertIsNotEnabled()
        compose.onNodeWithTag("礼金保存中").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("礼金保存栏").performTouchInput { repeat(3) { click() } }
        capture("loading-light")
        // 自动推进测试时钟会压缩 delay；计时段按墙钟节奏推进帧，保留真实停留时间。
        compose.mainClock.autoAdvance = false
        repository.gate!!.complete(Unit)
        val wallStart = SystemClock.uptimeMillis()
        val clockStart = compose.mainClock.currentTime
        val deadline = wallStart + 2000
        var successDisplayed = false
        while (returns.get() == 0 && SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy((SystemClock.uptimeMillis() - wallStart - (compose.mainClock.currentTime - clockStart)).coerceAtLeast(1L))
            SystemClock.sleep(16)
            if (!successDisplayed && viewModel.uiState.value.isSaved && compose.onNodeWithText("礼金记录已保存").isDisplayed()) {
                compose.onNodeWithText("礼金记录已保存").assertIsDisplayed()
                compose.onNodeWithTag("礼金保存栏").assertIsNotEnabled()
                successDisplayed = true
            }
        }
        compose.mainClock.autoAdvance = true
        assertTrue("返回前应展示成功反馈", successDisplayed)
        assertEquals(1, returns.get())
        val elapsed = returnedAt.get() - repository.savedAt.get()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "保存成功到返回实测：${elapsed}ms\n") })
        assertTrue("返回耗时应在350至600毫秒内，实际${elapsed}ms", elapsed in 350L..600L)
        assertEquals(1, repository.attempts.get())
        assertEquals(1, runBlocking { records.observeAll().first().size })
        restore.emulateSavedInstanceStateRestore()
        SystemClock.sleep(650)
        compose.waitForIdle()
        assertEquals("重建后不得重复返回", 1, returns.get())
    }

    @Test fun 失败保留表单不返回并可重试成功() {
        repository.fail = true
        start(dark = true)
        val original = viewModel.uiState.value
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.onNodeWithText("保存失败，请重试").assertIsDisplayed()
        compose.onNodeWithTag("礼金保存栏").assertIsEnabled()
        assertEquals(0, returns.get())
        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse("反馈应消费一次", viewModel.uiState.value.operationFailed)
        assertEquals(original.amount, viewModel.uiState.value.amount)
        assertEquals(original.notes, viewModel.uiState.value.notes)
        assertEquals(original.eventDate, viewModel.uiState.value.eventDate)
        compose.onNodeWithText("保存测试联系人").assertIsDisplayed()
        capture("failure-dark")
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.waitUntil { repository.attempts.get() == 2 }
        compose.onAllNodesWithText("保存失败，请重试").assertCountEquals(1)
        repository.fail = false
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.waitUntil(2000) { returns.get() == 1 }
        assertEquals(1, runBlocking { records.observeAll().first().size })
        assertEquals("保存测试备注", runBlocking { records.observeAll().first().single().record.notes })
    }

    @Test fun 创建自定义事件并重新编辑仍显示真实名称() {
        val editor = androidx.compose.runtime.mutableStateOf<GiftEditorViewModel?>(null)
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            editor.value = GiftEditorViewModel(repository, contacts, initialContactId = contactId).apply {
                update { it.copy(amount = "100", eventDate = 1789862400000L) }
            }
        }
        compose.setContent { LiZhangTheme {
            androidx.compose.runtime.key(editor.value) { AddGiftScreen(requireNotNull(editor.value)) { returns.incrementAndGet() } }
        } }
        compose.onNodeWithText("+ 自定义").performScrollTo().performClick()
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("请输入事件名称").assertIsDisplayed()
        compose.onNodeWithTag("自定义事件名称").performTextInput("长".repeat(21))
        compose.onNodeWithText("事件名称最多20个字符").assertIsDisplayed()
        compose.onNodeWithTag("自定义事件名称").performTextReplacement("  升学宴  ")
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("升学宴").assertIsSelected()
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.waitUntil(5000) { returns.get() == 1 }
        val saved = runBlocking { records.observeAll().first().single().record }
        assertEquals("升学宴", saved.customEventName)
        assertEquals(EventType.OTHER, saved.eventType)
        compose.runOnUiThread { editor.value = GiftEditorViewModel(repository, contacts, recordId = saved.id) }
        compose.onNodeWithText("升学宴").performScrollTo().assertIsSelected()
        capture("custom-event-edit")
        compose.onNodeWithText("生日").performClick()
        assertNull(editor.value!!.uiState.value.customEventName)
        compose.onNodeWithTag("礼金保存栏").performClick()
        compose.waitUntil(5000) { returns.get() == 2 }
        assertNull(runBlocking { records.observeRecord(saved.id).first() }?.customEventName)
    }

    @Test fun 备注键盘打开时保存栏可见并且关闭后回到底部() {
        start()
        val closedBottom = compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag("记账底部留白").performScrollTo()
        compose.onNodeWithText("备注").performClick()
        compose.waitUntil(5000) { imeBottom.get() > 0 }
        compose.onNodeWithTag("记账底部留白").performScrollTo()
        compose.onNodeWithTag("礼金保存栏").assertIsDisplayed()
        val bar = compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInRoot
        val notes = compose.onNodeWithText("备注").fetchSemanticsNode().boundsInRoot
        assertTrue("备注不能被保存栏覆盖", notes.bottom <= bar.top)
        assertTrue("保存栏应随键盘上移", bar.bottom < closedBottom)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val density = compose.activity.resources.displayMetrics.density
        val windowBottom = compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInWindow.bottom
        assertEquals("保存栏距键盘顶部只保留8dp，不应重复留白",
            device.displayHeight - imeBottom.get() - 8 * density, windowBottom, 3f)
        capture("keyboard-light")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5000) { imeBottom.get() == 0 }
        compose.waitForIdle()
        val restored = compose.onNodeWithTag("礼金保存栏").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals(closedBottom, restored, 2f)
    }
}

private class ControlledRepository(private val delegate: GiftRecordRepository) : GiftRecordRepository by delegate {
    var gate: CompletableDeferred<Unit>? = null
    @Volatile var fail = false
    val attempts = AtomicInteger()
    val savedAt = AtomicLong()
    override suspend fun create(record: GiftRecord): Long {
        attempts.incrementAndGet()
        gate?.await()
        if (fail) error("模拟写入失败")
        return delegate.create(record).also { savedAt.set(SystemClock.uptimeMillis()) }
    }
}
