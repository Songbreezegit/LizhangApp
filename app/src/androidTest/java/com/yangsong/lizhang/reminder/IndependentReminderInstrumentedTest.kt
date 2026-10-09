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
import com.yangsong.lizhang.ui.viewmodel.NotificationsViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** 只使用合成提醒，不创建联系人和礼金记录。 */
class IndependentReminderInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
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
    private fun 载入已过锚点的提醒(annually: Boolean): IndependentReminder {
        val date = LocalDate.now().minusYears(1).minusDays(1)
        val value = IndependentReminder(1, "历史测试安排", date, annually, lastNotifiedDate = date)
        instrumentation.runOnMainSync {
            // 模拟保存一年后再次打开应用，不修改设备时钟或任何用户数据。
            val json = JSONObject().apply {
                put("id", value.id); put("title", value.title); put("date", date.toString())
                put("annually", annually); put("enabled", true); put("notified", date.toString())
            }
            compose.activity.getSharedPreferences("reminder_preferences", 0).edit()
                .putString("independent_reminders", JSONArray().put(json).toString()).putLong("next_id", 2).commit()
            (repository as AndroidReminderRepository).reminders.value = AndroidReminderRepository(compose.activity).reminders.value
        }
        compose.waitForIdle()
        return value
    }

    @Test fun 年度历史锚点可修改名称且启停后可继续编辑并保留消费记录() {
        val original = 载入已过锚点的提醒(true)
        for ((index, enabled) in listOf(true, false, true).withIndex()) {
            if (repository.reminders.value.single().enabled != enabled) {
                compose.onNodeWithTag("独立提醒开关-${original.id}").performScrollTo().performClick()
                compose.waitUntil(3000) { repository.reminders.value.single().enabled == enabled }
            }
            compose.onNodeWithTag("独立提醒编辑-${original.id}").performScrollTo().performClick()
            compose.onNodeWithTag("独立提醒名称").performTextReplacement("修改测试安排$index")
            compose.onNodeWithTag("独立提醒保存").assertIsEnabled().performClick()
            compose.waitUntil(3000) { repository.reminders.value.single().title == "修改测试安排$index" }
            val saved = AndroidReminderRepository(compose.activity).reminders.value.single()
            assertEquals(original.date, saved.date)
            assertEquals(original.lastNotifiedDate, saved.lastNotifiedDate)
            assertEquals(enabled, saved.enabled)
        }
        compose.onNodeWithTag("独立提醒编辑-${original.id}").performScrollTo().performClick()
        compose.onNodeWithTag("独立提醒每年重复").performClick()
        compose.onNodeWithTag("独立提醒保存").assertIsNotEnabled()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
    }

    @Test fun 过期一次提醒在编辑器和仓库及ViewModel均不能保存() {
        val original = 载入已过锚点的提醒(false)
        compose.onNodeWithTag("独立提醒编辑-${original.id}").performScrollTo().performClick()
        compose.onNodeWithTag("独立提醒名称").performTextReplacement("修改测试安排")
        compose.onNodeWithTag("独立提醒保存").assertIsNotEnabled()
        instrumentation.runOnMainSync {
            val edited = original.copy(title = "修改测试安排")
            assertFalse(NotificationsViewModel(repository).saveReminder(edited))
            assertTrue(runCatching { repository.save(edited) }.isFailure)
            assertEquals(original, repository.reminders.value.single())
        }
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
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
