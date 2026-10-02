package com.yangsong.lizhang.reminder

import android.content.Intent
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.*
import com.yangsong.lizhang.core.common.ReminderNavigationContract
import com.yangsong.lizhang.data.reminder.AndroidReminderRepository
import com.yangsong.lizhang.domain.reminder.IndependentReminder
import java.time.LocalDate
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** 只使用合成提醒，不创建联系人和礼金记录。 */
class IndependentReminderInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val repository get() = (compose.activity.application as LiZhangApplication).appContainer.reminderRepository
    @Before fun 准备中文空提醒页() {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
            repository.setEnabled(false)
            repository.reminders.value.forEach { repository.delete(it.id) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("提醒").performClick()
        compose.waitForIdle()
    }
    @After fun 清理合成提醒() {
        instrumentation.runOnMainSync { repository.reminders.value.forEach { repository.delete(it.id) } }
    }
    @Test fun 空状态没有重复猫且新增默认一次编辑停用删除完整可用() {
        compose.onNodeWithTag("提醒空状态").performScrollTo().assertIsDisplayed()
        val image = instrumentation.uiAutomation.takeScreenshot()!!
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
        File(folder, "独立提醒空状态.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        compose.onNodeWithTag("新增独立提醒").performScrollTo().performClick()
        compose.onNodeWithTag("独立提醒每年重复").assertIsOff()
        compose.onNodeWithTag("独立提醒名称").performTextInput("测试安排")
        compose.onNodeWithTag("独立提醒保存").performClick()
        compose.waitUntil(3000) { repository.reminders.value.size == 1 }
        val value = repository.reminders.value.single()
        assertEquals(LocalDate.now().plusDays(1), value.date)
        assertFalse(value.annually)
        compose.onNodeWithTag("独立提醒编辑-${value.id}").performScrollTo().performClick()
        compose.onNodeWithTag("独立提醒每年重复").performClick()
        compose.onNodeWithTag("独立提醒保存").performClick()
        compose.waitUntil(3000) { repository.reminders.value.single().annually }
        compose.onNodeWithTag("独立提醒开关-${value.id}").performScrollTo().performClick()
        compose.waitUntil(3000) { !repository.reminders.value.single().enabled }
        compose.onNodeWithTag("独立提醒删除-${value.id}").performScrollTo().performClick()
        compose.onNode(hasText("删除") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("提醒空状态").performScrollTo().assertIsDisplayed()
    }
    @Test fun 私有存储重载保持状态且今天过去日期被拒绝() {
        instrumentation.runOnMainSync {
            val tomorrow = LocalDate.now().plusDays(1)
            repository.save(IndependentReminder(title = "测试安排", date = tomorrow))
            val id = repository.reminders.value.single().id
            repository.markNotified(id, tomorrow)
            val reloaded = AndroidReminderRepository(compose.activity.applicationContext)
            assertEquals(repository.reminders.value, reloaded.reminders.value)
            assertTrue(runCatching { repository.save(IndependentReminder(title = "测试安排", date = LocalDate.now())) }.isFailure)
            assertTrue(runCatching { repository.save(IndependentReminder(title = "测试安排", date = LocalDate.now().minusDays(1))) }.isFailure)
        }
        val activity = compose.activity
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertNotSame(activity, compose.activity)
        compose.onNodeWithText("我的提醒").performScrollTo().assertIsDisplayed()
        // 返回后重建保持导航恢复结果。
        compose.onNodeWithContentDescription("返回").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("提醒").assertIsDisplayed()
    }
}
