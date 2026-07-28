package com.yangsong.lizhang.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import android.view.accessibility.AccessibilityNodeInfo
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.Contact
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
    fun `联系人详情新增往来会预选联系人且日期不能超过今年`() {
        runBlocking {
            application.appContainer.contactRepository.create(Contact(name = "流程测试联系人"))
        }
        launchApp()

        waitForText("礼金记账")
        clickText("联系人")
        waitForText("流程测试联系人")
        clickText("流程测试联系人")
        waitForText("新增往来")
        clickText("新增往来")

        waitForText("记一笔")
        assertNotNull(device.findObject(By.text("流程测试联系人")))

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

    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        node ?: return null
        if (node.text?.toString() == text) return node
        repeat(node.childCount) { index ->
            findNodeByText(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private suspend fun clearLedger() {
        application.appContainer.giftRecordRepository.observeAll().first()
            .forEach { application.appContainer.giftRecordRepository.delete(it.record) }
        application.appContainer.contactRepository.observeContacts().first()
            .forEach { application.appContainer.contactRepository.delete(it) }
    }
}
