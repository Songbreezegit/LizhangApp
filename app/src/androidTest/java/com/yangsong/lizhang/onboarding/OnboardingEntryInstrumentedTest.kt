package com.yangsong.lizhang.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 通过 adb 全新安装后单独执行，验证真实 App 入口及再次启动。 */
class OnboardingEntryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitForPage(page: Int) {
        try {
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithTag("引导页面$page").assertIsDisplayed(); true }.getOrDefault(false)
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            val indicator = compose.onAllNodesWithTag("引导页码").fetchSemanticsNodes().map {
                it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }
            }
            throw AssertionError("引导目标页 $page 未显示，当前页码为 $indicator", error)
        }
    }

    @Test fun 全新安装三页完成后再次启动不重复且设置重看返回原页() {
        val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
        assertFalse("此验收必须从卸载后全新安装开始", app.appContainer.onboardingRepository.state.value.completed)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForPage(1)
            compose.onNodeWithTag("引导下一步").performClick()
            waitForPage(2)
            scenario.recreate()
            waitForPage(2)
            compose.onNodeWithTag("引导下一步").performClick()
            waitForPage(3)
            assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS))
            if (Build.VERSION.SDK_INT >= 33) assertEquals(PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS))
            compose.onNodeWithTag("引导完成").performClick()
            compose.waitUntil(5000) { app.appContainer.onboardingRepository.state.value.completed }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("首页列表").fetchSemanticsNodes().isNotEmpty() }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(5000) { compose.onAllNodesWithTag("首页列表").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("引导页面1").assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.nav_settings)).performClick()
            // 最小滚动会把新入口留在浮动导航下面；先把完整的第三组滚到可点击区域。
            compose.onNodeWithTag("设置列表").performScrollToIndex(3)
            compose.onNodeWithText(app.getString(R.string.settings_onboarding)).performClick()
            waitForPage(1)
            compose.onNodeWithTag("引导下一步").performClick()
            compose.onNodeWithTag("引导下一步").performClick()
            compose.onNodeWithText(app.getString(R.string.onboarding_done)).performClick()
            compose.onNodeWithTag("设置列表").assertExists()
            assertTrue(app.appContainer.onboardingRepository.state.value.completed)
        }
    }
}
