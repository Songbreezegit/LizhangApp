package com.yangsong.lizhang.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicLedgerFlowInstrumentedTest {
    private val application: LiZhangApplication
        get() = ApplicationProvider.getApplicationContext()
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = runBlocking {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        clearLedger()
    }

    @After
    fun tearDown() = runBlocking {
        scenario?.close()
        clearLedger()
    }

    @Test
    fun `联系人详情新增往来会预选联系人且日期不能超过真实年份`() {
        runBlocking {
            application.appContainer.contactRepository.create(Contact(name = "流程测试联系人"))
        }
        launchApp()

        waitForText("礼金记账")
        clickText("联系人")
        scrollToText("流程测试联系人")
        waitForText("流程测试联系人")
        clickText("流程测试联系人")
        waitForText("新增往来")
        clickText("新增往来")

        waitForText("记一笔")
        waitForText("流程测试联系人")

        val today = DateFormatter.format(System.currentTimeMillis())
        waitForText(today)
        clickText(today)
        waitForText("选择日期")
        val nextYear = device.wait(Until.findObject(By.desc("下一年")), 10_000)
        assertNotNull(nextYear)
        var nextYearControl: UiObject2 = requireNotNull(nextYear)
        while (nextYearControl.isEnabled) {
            nextYearControl = nextYearControl.parent ?: break
        }
        assertFalse("当前年份不应允许继续增加", nextYearControl.isEnabled)
    }

    @Test
    fun `大量搜索结果由可滚动列表承载且可定位最后联系人`() {
        seedContactsAndRecords(24)
        launchApp()

        waitForText("礼金记账")
        clickText("全部")
        waitForText("搜索")
        setFirstTextField("批量搜索")

        waitForText("批量搜索01")
        assertNotNull(
            "大量搜索结果应由可滚动列表承载",
            findScrollableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow),
        )
        setFirstTextField("批量搜索24")
        waitForText("批量搜索24")
    }

    @Test
    fun `大量联系人选择器可滚动且可搜索选中`() {
        seedContactsAndRecords(24, includeRecords = false)
        launchApp()

        waitForText("礼金记账")
        clickText("记一笔")
        waitForText("选择已有联系人")
        clickText("选择已有联系人")

        waitForText("批量搜索01")
        assertNotNull(
            "大量联系人应由可滚动列表承载",
            findScrollableNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow),
        )
        setFirstTextField("批量搜索24")
        waitForText("批量搜索24")
        clickText("批量搜索24")
        waitForText("批量搜索24")
    }

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(application.packageName)), 10_000)
        device.waitForIdle()
    }

    private fun waitForText(text: String) {
        if (!device.wait(Until.hasObject(By.text(text)), 10_000)) {
            val visibleTexts = device.findObjects(By.clazz("android.widget.TextView"))
                .mapNotNull { it.text }
                .filter { it.isNotBlank() }
                .joinToString()
            fail(
                "未在当前界面找到文本：$text；当前前台包名：${device.currentPackageName}；" +
                    "当前可见文本：$visibleTexts",
            )
        }
    }

    /** 小屏幕上新增导入入口后，联系人行可能位于首屏以下。 */
    private fun scrollToText(text: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(5) {
            if (device.wait(Until.hasObject(By.text(text)), 500)) return
            findScrollableNode(instrumentation.uiAutomation.rootInActiveWindow)
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            instrumentation.waitForIdleSync()
        }
        waitForText(text)
    }

    private fun clickText(text: String) {
        waitForText(text)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var target = findNodeByText(instrumentation.uiAutomation.rootInActiveWindow, text)
            ?: throw AssertionError("未找到可点击文本：$text")
        while (!target.isClickable) {
            target = target.parent ?: break
        }
        if (!target.isClickable) fail("文本没有可点击的父节点：$text")
        if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            fail("无法执行点击操作：$text")
        }
        instrumentation.waitForIdleSync()
    }

    private fun setFirstTextField(text: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val textField = findEditableNode(
            instrumentation.uiAutomation.rootInActiveWindow,
        ) ?: throw AssertionError("当前页面没有可编辑文本框")
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }
        if (!textField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            fail("无法设置搜索文本")
        }
        instrumentation.waitForIdleSync()
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        node ?: return null
        if (node.text?.toString() == text) return node
        repeat(node.childCount) { index ->
            findNodeByText(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable || node.actionList.any {
                it.id == AccessibilityNodeInfo.ACTION_SET_TEXT
            }
        ) return node
        repeat(node.childCount) { index ->
            findEditableNode(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        repeat(node.childCount) { index ->
            findScrollableNode(node.getChild(index))?.let { return it }
        }
        return node.takeIf {
            it.isScrollable && !it.isEditable
        }
    }

    private fun seedContactsAndRecords(
        count: Int,
        includeRecords: Boolean = true,
    ) = runBlocking {
        repeat(count) { index ->
            val sequence = index + 1
            val contactId = application.appContainer.contactRepository.create(
                Contact(name = "批量搜索${sequence.toString().padStart(2, '0')}"),
            )
            if (includeRecords) {
                application.appContainer.giftRecordRepository.create(
                    GiftRecord(
                        contactId = contactId,
                        amountInCents = sequence * 10_000L,
                        eventType = EventType.OTHER,
                        eventDate = System.currentTimeMillis() - index * DAY_MILLIS,
                        direction = GiftDirection.RECEIVED,
                        notes = "批量搜索验证",
                    ),
                )
            }
        }
    }

    private suspend fun clearLedger() {
        application.appContainer.giftRecordRepository.observeAll().first()
            .forEach { application.appContainer.giftRecordRepository.delete(it.record) }
        application.appContainer.contactRepository.observeContacts().first()
            .forEach { application.appContainer.contactRepository.delete(it) }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
