package com.yangsong.lizhang.reminder

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNavigationInstrumentedTest {
    private val application: LiZhangApplication
        get() = ApplicationProvider.getApplicationContext()
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before
    fun setUp() = runBlocking {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        clearLedger()
    }

    @After
    fun tearDown() = runBlocking {
        device.pressHome()
        clearLedger()
    }

    @Test
    fun `有效提醒Intent冷启动后直达对应礼金详情`() {
        runBlocking {
            val contactId = application.appContainer.contactRepository.create(Contact(name = "通知测试"))
            val recordId = application.appContainer.giftRecordRepository.create(
                GiftRecord(
                    contactId = contactId,
                    amountInCents = 66_600,
                    eventType = EventType.BIRTHDAY,
                    eventDate = 1_752_787_200_000,
                    direction = GiftDirection.RECEIVED,
                ),
            )

            launchReminder(recordId)

            waitForText("礼金记录详情")
            assertNotNull(device.findObject(By.text("通知测试")))
        }
    }

    @Test
    fun `记录已删除时回到首页并显示居中提示`() {
        launchReminder(999)

        waitForText("该提醒对应的礼金记录已不存在")
        assertNotNull(device.findObject(By.text("礼金记账")))
    }

    @Test
    fun `不同记录使用不同Intent标识避免通知互相覆盖`() {
        val first = ReminderNavigationContract.createOpenRecordIntent(application, 1)
        val second = ReminderNavigationContract.createOpenRecordIntent(application, 2)

        assertNotEquals(first.data, second.data)
        assertEquals(1L, ReminderNavigationContract.readRecordId(first))
        assertEquals(2L, ReminderNavigationContract.readRecordId(second))
    }

    private fun launchReminder(recordId: Long) {
        application.startActivity(
            ReminderNavigationContract.createOpenRecordIntent(application, recordId).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
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

    private suspend fun clearLedger() {
        application.appContainer.giftRecordRepository.observeAll().first()
            .forEach { application.appContainer.giftRecordRepository.delete(it.record) }
        application.appContainer.contactRepository.observeContacts().first()
            .forEach { application.appContainer.contactRepository.delete(it) }
    }
}
