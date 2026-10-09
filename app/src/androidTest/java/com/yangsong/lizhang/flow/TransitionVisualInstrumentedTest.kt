package com.yangsong.lizhang.flow

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.domain.model.AppThemeMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 空白测试设备的设置页证据；不读取或创建联系人、金额、备份。 */
class TransitionVisualInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = compose.activity.application as LiZhangApplication

    @Before fun 打开中文设置页() {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
            app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT)
        }
        compose.waitForIdle()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("深色模式").performScrollTo()
    }

    private fun frame(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("设备未返回绘制帧")
        // 实际画面需有非均匀内容，防止整页空屏被进度断言漏过。
        val colors = mutableSetOf<Int>()
        for (y in bitmap.height / 8 until bitmap.height * 7 / 8 step 13) {
            for (x in bitmap.width / 8 until bitmap.width * 7 / 8 step 13) colors += bitmap.getPixel(x, y)
        }
        assertTrue("实际绘制帧具有页面内容", colors.size > 60)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "transition-evidence").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun 双向圆形真实帧和弹窗连续颜色() {
        frame("圆形浅色起点")
        for ((index, label) in listOf("浅到深", "深到浅").withIndex()) {
            val before = compose.activity.appearanceState.circularHandoffs
            val previousMode = app.appContainer.themeRepository.themeMode.value
            compose.onNode(isToggleable()).performClick()
            compose.waitUntil(2000) { app.appContainer.themeRepository.themeMode.value != previousMode }
            // 测试运行器的 Compose 时钟需先提交目标组合；原生动画仍按设备帧运行。
            compose.waitForIdle()
            var captured = 0
            val deadline = android.os.SystemClock.uptimeMillis() + 1800
            do {
                frame("圆形${label}_${captured++}")
            } while (compose.activity.appearanceState.snapshot != null && android.os.SystemClock.uptimeMillis() < deadline)
            assertNull(compose.activity.appearanceState.snapshot)
            if (ValueAnimator.areAnimatorsEnabled()) assertEquals("圆形过渡必须由实际绘制启动：$label", before + 1, compose.activity.appearanceState.circularHandoffs)
        }
        compose.onNodeWithText("主题设置").performClick()
        val dialogBounds = compose.onNode(isDialog()).fetchSemanticsNode().boundsInWindow
        compose.onAllNodes(isSelectable() and hasAnyAncestor(isDialog()))[2].performClick()
        frame("弹窗深色预览")
        compose.onNode(isDialog()).assertExists()
        assertEquals(dialogBounds, compose.onNode(isDialog()).fetchSemanticsNode().boundsInWindow)
        compose.onAllNodes(isSelectable() and hasAnyAncestor(isDialog()))[1].performClick()
        frame("弹窗浅色预览")
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("完成").performClick()
    }

    @Test fun 连续操作旋转与后台后释放画面并保留最后目标() {
        val switch = compose.onNode(isToggleable())
        switch.performClick()
        switch.performClick()
        switch.performClick()
        compose.waitUntil(3000) { compose.activity.appearanceState.snapshot == null }
        switch.assertIsOn()
        switch.performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertNull(compose.activity.appearanceState.snapshot)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("语言").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.waitUntil(3000) { compose.activity.appearanceState.snapshot == null }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        compose.waitForIdle()
        compose.waitUntil(5000) { compose.onAllNodesWithText("语言").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("语言").performScrollTo().assertIsDisplayed()
        instrumentation.runOnMainSync { app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT) }
    }
}
