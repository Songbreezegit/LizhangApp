package com.yangsong.lizhang.flow

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.theme.LocalThemeDarkFraction
import com.yangsong.lizhang.ui.viewmodel.AppearanceTransitionViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 使用独立动画时钟和内存状态，不改写系统倍率、偏好或业务数据。 */
@OptIn(ExperimentalTestApi::class)
class ColorTransitionLifecycleInstrumentedTest {
    private val durationScale = object : MotionDurationScale { override val scaleFactor = 10f }
    @get:Rule val compose = createComposeRule(effectContext = durationScale)
    private lateinit var state: AppearanceTransitionViewModel
    private var hostId = 0L
    private val dark = mutableStateOf(false)
    private var fraction = 0f

    private fun bind() {
        compose.runOnIdle {
            state = AppearanceTransitionViewModel()
            hostId = state.nextHost()
            state.observeSnapshots(hostId) {}
        }
        compose.setContent {
            val request = state.themeRequest
            LiZhangTheme(darkTheme = dark.value, animateColors = state.animateColors,
                colorTransitionId = request?.id, colorTargetDark = request?.targetDark ?: dark.value,
                stopColorTransition = request?.colorPhase == AppearanceTransitionViewModel.ColorPhase.SETTLING,
                onColorsStarted = { id, target -> state.beginColorExecution(hostId, id, target) },
                onColorsFinished = { id, execution, target -> state.finishColors(hostId, id, execution, target) }) {
                val currentFraction = LocalThemeDarkFraction.current
                SideEffect { fraction = currentFraction }
                Box {}
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun waitPastWaitingDeadline() {
        val delivered = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        val signal = Runnable { delivered.countDown() }
        handler.postDelayed(signal, 1700L)
        try { assertTrue("主线程必须越过真实等待时限", delivered.await(4, TimeUnit.SECONDS)) }
        finally { handler.removeCallbacks(signal) }
    }

    private fun release() {
        compose.runOnIdle { state.clear(); state.releaseSnapshotObserver(hostId) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun 十倍渐变跨越一点五秒后仍拒绝输入并在实际完成后解锁() {
        bind()
        try {
            compose.runOnIdle {
                state.animateColors = true
                state.beginTheme(hostId, true, colors = true)
                dark.value = true
            }
            compose.mainClock.advanceTimeBy(1600L)
            waitPastWaitingDeadline()
            compose.runOnIdle {
                assertTrue(state.isThemeTransitioning)
                assertEquals(AppearanceTransitionViewModel.ColorPhase.RUNNING, state.themeRequest!!.colorPhase)
                assertTrue("配色仍处于中间帧", fraction > 0f && fraction < 1f)
                assertNull(state.beginTheme(hostId, false, colors = true))
            }
            compose.mainClock.advanceTimeBy(1200L)
            compose.runOnIdle {
                assertFalse(state.isThemeTransitioning)
                assertFalse(state.animateColors)
                assertEquals(1f, fraction, 0.001f)
            }
        } finally { release() }
    }

    @Test fun 等待超时保持互斥直到实际配色收敛且不再启动渐变() {
        bind()
        try {
            compose.runOnIdle {
                state.animateColors = true
                state.beginTheme(hostId, true, colors = true)
            }
            compose.mainClock.advanceTimeByFrame()
            waitPastWaitingDeadline()
            compose.runOnIdle {
                assertTrue(state.isThemeTransitioning)
                assertEquals(AppearanceTransitionViewModel.ColorPhase.SETTLING, state.themeRequest!!.colorPhase)
                assertNull(state.beginTheme(hostId, false, colors = true))
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertFalse(state.isThemeTransitioning)
                assertFalse(state.animateColors)
                assertEquals(0f, fraction, 0.001f)
            }
        } finally { release() }
    }
}
