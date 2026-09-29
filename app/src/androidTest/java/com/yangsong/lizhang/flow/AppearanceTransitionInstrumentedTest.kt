package com.yangsong.lizhang.flow

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.yangsong.lizhang.ui.component.AppearanceTransition
import com.yangsong.lizhang.ui.component.LocalAppearanceOpacity
import com.yangsong.lizhang.ui.component.LocalAppearanceTransition
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceTransitionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun 首屏不淡入且切换中间帧连续并防止重复提交() {
        var request: (() -> Unit) -> Unit = { it() }
        var opacity: () -> Float = { 1f }
        var changes = 0
        compose.setContent {
            LiZhangTheme {
                AppearanceTransition {
                    val currentRequest = LocalAppearanceTransition.current
                    val currentOpacity = LocalAppearanceOpacity.current
                    SideEffect { request = currentRequest; opacity = currentOpacity }
                }
            }
        }
        compose.runOnIdle { assertEquals(1f, opacity(), 0f) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { request { changes++ }; request { changes++ } }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue(opacity() > 0f && opacity() < 1f)
            assertEquals(0, changes)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, changes); assertEquals(1f, opacity(), 0f) }
        compose.runOnIdle { request { changes++ } }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(2, changes); assertEquals(1f, opacity(), 0f) }
    }

    @Test
    fun 切换后的重建接续淡入并最终恢复可见() {
        val restoration = StateRestorationTester(compose)
        var request: (() -> Unit) -> Unit = { it() }
        var opacity: () -> Float = { 1f }
        restoration.setContent {
            LiZhangTheme {
                AppearanceTransition {
                    val currentRequest = LocalAppearanceTransition.current
                    val currentOpacity = LocalAppearanceOpacity.current
                    SideEffect { request = currentRequest; opacity = currentOpacity }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { request {} }
        compose.mainClock.advanceTimeBy(144)
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1f, opacity(), 0f) }
    }
}
