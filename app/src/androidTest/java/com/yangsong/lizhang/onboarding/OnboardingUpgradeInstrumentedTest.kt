package com.yangsong.lizhang.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 外部先安装 0.9.2，再以 adb install -r 升级；包括旧版未启动的安装。 */
class OnboardingUpgradeInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun 从旧版本覆盖安装保留引导状态并在确认新告知后进入首页() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        val container = app.appContainer
        assertTrue(container.onboardingRepository.state.value.completed)
        val before = container.onboardingRepository.state.value
        assertFalse("旧安装不能自动视为同意新增的政策", container.canProcessPersonalData)
        assertFalse(container.isBusinessDatabaseInitialized)
        assertFalse(container.isReminderRepositoryInitialized)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(8000) { compose.onAllNodesWithTag("首次隐私告知").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("首页列表").assertDoesNotExist()
            compose.onNodeWithTag("隐私告知拒绝").performClick()
            compose.waitUntil(5000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
            assertEquals(before, container.onboardingRepository.state.value)
            assertFalse(container.isBusinessDatabaseInitialized)
            assertFalse(container.isReminderRepositoryInitialized)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(8000) {
                runCatching { compose.onNodeWithTag("隐私告知同意").assertIsEnabled(); true }.getOrDefault(false)
            }
            compose.onNodeWithTag("隐私告知同意").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("首页列表").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("引导页面1").assertDoesNotExist()
            compose.onNodeWithTag("功能引导气泡").assertDoesNotExist()
            assertEquals(before, container.onboardingRepository.state.value)
            assertTrue(container.canProcessPersonalData)
            assertTrue(app.getSharedPreferences("onboarding_preferences", 0).getBoolean("onboarding_completed", false))
        }
    }
}
