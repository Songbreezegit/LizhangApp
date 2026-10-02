package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.component.SnapshotOverlay
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 不创建隐私数据；实际 Canvas 像素和 Activity 页面均在模拟器验证。 */
class AppearanceTransitionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun 圆形裁剪真实像素从中心展开并覆盖四角() {
        compose.runOnIdle {
            val state = AppearanceTransitionViewModel()
            val old = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val shot = AppearanceTransitionViewModel.Snapshot(0, old, 240, 400, 194f, 286f, true, null)
            state.install(shot)
            val view = SnapshotOverlay(compose.activity, state).apply { layout(0, 0, 240, 400) }
            for (progress in listOf(0f, .2f, .5f, 1f)) {
                shot.progress = progress
                val frame = Bitmap.createBitmap(240, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                view.draw(Canvas(frame))
                assertEquals(if (progress == 0f) Color.RED else Color.BLUE, frame.getPixel(194, 286))
                if (progress == 1f) listOf(0 to 0, 239 to 0, 0 to 399, 239 to 399).forEach { (x, y) ->
                    assertEquals("四角没有旧画面残留", Color.BLUE, frame.getPixel(x, y))
                }
                // 内容始终为旧/新画面中的一种，没有透明或空白像素。
                for (y in 0 until 400 step 7) for (x in 0 until 240 step 7) {
                    val pixel = frame.getPixel(x, y)
                    assertEquals(255, Color.alpha(pixel))
                    assertEquals(0, Color.green(pixel))
                    assertTrue(Color.red(pixel) + Color.blue(pixel) in 254..255)
                }
                frame.recycle()
            }
            state.clear()
        }
    }

    @Test
    fun 系统深色开关显示有效状态且圆心匹配控件并清理中断() {
        val app = compose.activity.application as com.yangsong.lizhang.LiZhangApplication
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("zh-CN"))
        }
        compose.waitForIdle()
        compose.runOnIdle { app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT) }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("深色模式").performScrollTo()
        val switch = compose.onNode(isToggleable())
        switch.assertIsOff()
        val bounds = switch.fetchSemanticsNode().boundsInWindow
        switch.performClick()
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            compose.waitUntil(2000) { compose.activity.appearanceState.snapshot != null }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val shot = compose.activity.appearanceState.snapshot!!
            assertEquals(bounds.center.x, shot.x, 2f)
            assertEquals(bounds.center.y, shot.y, 2f)
            assertTrue(shot.bitmap.allocationByteCount <= 8 * 1024 * 1024)
            }
        } else {
            assertNull(compose.activity.appearanceState.snapshot)
        }
        compose.waitForIdle()
        compose.waitUntil(2500) { compose.activity.appearanceState.snapshot == null }
        switch.assertIsOn()
        compose.onNodeWithText("主题设置").performClick()
        compose.onAllNodes(isSelectable() and hasAnyAncestor(isDialog()))[0].performClick()
        compose.onNodeWithText("完成").performClick()
        val systemDark = compose.activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (systemDark) switch.assertIsOn() else switch.assertIsOff()
        switch.performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(2500) { compose.activity.appearanceState.snapshot == null }
        compose.onNodeWithText("语言").assertIsDisplayed()
        compose.runOnIdle { app.appContainer.themeRepository.setThemeMode(AppThemeMode.LIGHT) }
    }
}
