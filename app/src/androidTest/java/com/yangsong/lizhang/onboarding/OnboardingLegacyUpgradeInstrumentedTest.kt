package com.yangsong.lizhang.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule

/** 仅在隔离模拟器生成无电话的合成测试记录；两个阶段之间覆盖安装新版。 */
class OnboardingLegacyUpgradeInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun 旧版本建立升级测试样本() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyUpgradePhase") == "setup")
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val container = app.appContainer
        // 建样阶段明确同意，仅准备隔离测试数据；验收阶段禁止预置同意。
        assertTrue(container.privacyConsentRepository.acceptCurrentPolicy())
        val id = container.contactRepository.create(Contact(name = "引导升级合成样本", createdTime = 1_700_000_000_000L))
        container.giftRecordRepository.create(GiftRecord(contactId = id, amountInCents = 100,
            eventType = EventType.OTHER, eventDate = 1_700_000_000_000L, direction = GiftDirection.RECEIVED,
            createdTime = 1_700_000_000_000L, customEventName = "升级测试"))
        container.themeRepository.setThemeMode(AppThemeMode.DARK)
        container.reminderRepository.setEnabled(true)
        assertEquals(1, container.giftRecordRepository.observeAll().first().size)
        assertEquals(AppThemeMode.DARK, container.themeRepository.themeMode.value)
        assertTrue(container.reminderRepository.settings.value.enabled)
        // 测试进程随后会被安装器终止；等待旧仓库的 apply 写盘，不改变其实现。
        for (name in listOf("礼账显示设置", "reminder_preferences")) {
            assertTrue(app.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().commit())
        }
    }

    @Test fun 新版本等待隐私确认且保留旧数据设置与已完成引导() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyUpgradePhase") == "check")
        val container = ApplicationProvider.getApplicationContext<LiZhangApplication>().appContainer
        assertTrue(container.onboardingRepository.state.value.completed)
        val before = container.onboardingRepository.state.value
        assertFalse("需从没有当前版本确认记录的旧安装升级", container.canProcessPersonalData)
        assertFalse(container.isBusinessDatabaseInitialized)
        assertFalse(container.isReminderRepositoryInitialized)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(8000) { compose.onAllNodesWithTag("首次隐私告知").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("首页列表").assertDoesNotExist()
            compose.onNodeWithTag("隐私告知拒绝").performScrollTo().performClick()
            assertFalse(container.canProcessPersonalData)
            assertEquals(before, container.onboardingRepository.state.value)
            assertFalse(container.isBusinessDatabaseInitialized)
            assertFalse(container.isReminderRepositoryInitialized)
            compose.onNodeWithTag("返回隐私告知").performScrollTo().performClick()
            compose.onNodeWithTag("隐私告知同意").performScrollTo().performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithTag("首页列表").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
            assertEquals(before, container.onboardingRepository.state.value)
        }
        val contacts = container.contactRepository.observeContacts().first()
        val records = container.giftRecordRepository.observeAll().first()
        assertEquals(1, contacts.size)
        assertEquals(1, records.size)
        assertEquals(contacts.single().id, records.single().record.contactId)
        assertEquals("升级测试", records.single().record.customEventName)
        assertEquals(AppThemeMode.DARK, container.themeRepository.themeMode.value)
        assertTrue(container.reminderRepository.settings.value.enabled)
    }
}
