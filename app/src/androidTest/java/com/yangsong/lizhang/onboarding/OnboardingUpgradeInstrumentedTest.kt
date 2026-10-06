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

    @Test fun 从旧版本覆盖安装后首次入口直接显示首页() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        assertTrue(app.appContainer.onboardingRepository.state.value.completed)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(5000) { compose.onAllNodesWithTag("首页列表").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("引导页面1").assertDoesNotExist()
            assertTrue(app.getSharedPreferences("onboarding_preferences", 0).getBoolean("onboarding_completed", false))
        }
    }
}
