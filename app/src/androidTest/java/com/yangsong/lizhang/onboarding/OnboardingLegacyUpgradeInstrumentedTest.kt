package com.yangsong.lizhang.onboarding

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 仅在隔离模拟器生成无电话的合成测试记录；两个阶段之间覆盖安装新版。 */
class OnboardingLegacyUpgradeInstrumentedTest {
    @Test fun 旧版本建立升级测试样本() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyUpgradePhase") == "setup")
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val container = app.appContainer
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

    @Test fun 新版本保留旧数据和设置并跳过引导() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("legacyUpgradePhase") == "check")
        val container = ApplicationProvider.getApplicationContext<LiZhangApplication>().appContainer
        assertTrue(container.onboardingRepository.state.value.completed)
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
