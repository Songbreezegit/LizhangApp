package com.yangsong.lizhang.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupAnimationViewModelTest {
    @Test fun 一致快照与窗口帧时间线采用同一单调时钟且不改变时间轴() {
        var now = 1_000L
        val state = StartupAnimationViewModel(monotonicClock = { now }, diagnosticsEnabled = true)
        state.initialize(eligible = true, animationsEnabled = true)
        state.recordWindowEvent("内容首帧已提交", 7)
        now = 2_000L
        state.markReady()
        state.onFrame(1_000_000_000L)
        now = 3_000L
        state.onFrame(1_440_000_000L)
        val snapshot = state.diagnosticSnapshot()
        assertEquals(3_000L, snapshot.monotonicNanos)
        assertTrue(snapshot.visible && snapshot.ready && snapshot.initialized)
        assertEquals(.5f, snapshot.progress, .001f)
        assertEquals(1_000_000_000L, snapshot.firstFrameNanos)
        val events = state.diagnostics()
        assertEquals(7, events.first { it.event == "内容首帧已提交" }.hostId)
        assertEquals(1_440_000_000L, events.last().frameNanos)
        assertEquals(.5f, events.last().state.progress, .001f)
        assertTrue(events.zipWithNext().all { (first, second) -> first.state.monotonicNanos <= second.state.monotonicNanos })
    }

    @Test fun Release关闭诊断后不分配逐帧证据而保持固定时长() {
        val state = StartupAnimationViewModel(diagnosticsEnabled = false)
        state.initialize(eligible = true, animationsEnabled = true)
        state.recordWindowEvent("内容首帧已提交", 7)
        state.markReady()
        state.onFrame(1_000_000_000L)
        state.onFrame(1_880_000_000L)
        assertTrue(state.diagnostics().isEmpty())
        assertFalse(state.visible)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 配置重建保留时间轴并在固定时长结束() {
        val state = StartupAnimationViewModel()
        state.initialize(eligible = true, animationsEnabled = true)
        state.markReady()
        state.onFrame(1_000_000_000L)
        state.onFrame(1_440_000_000L)
        assertTrue(state.visible)
        assertEquals(.5f, state.progress, .001f)
        state.initialize(eligible = false, animationsEnabled = true)
        assertTrue(state.visible)
        assertEquals(.5f, state.progress, .001f)
        state.onFrame(1_880_000_000L)
        assertFalse(state.visible)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 系统动画关闭和直接入口没有额外等待() {
        for ((eligible, enabled) in listOf(true to false, false to true)) {
            val state = StartupAnimationViewModel()
            state.initialize(eligible, enabled)
            assertFalse(state.visible)
            assertEquals(1f, state.progress, 0f)
        }
    }

    @Test fun 通知或退后台中断后旧帧不能重新覆盖页面() {
        val state = StartupAnimationViewModel()
        state.initialize(eligible = true, animationsEnabled = true)
        state.markReady()
        state.onFrame(0L)
        state.onFrame(200_000_000L)
        state.finish()
        state.onFrame(400_000_000L)
        state.initialize(eligible = true, animationsEnabled = true)
        assertFalse(state.visible)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 首次可见绘制之前不消耗动画时间且新窗口重新等待() {
        val state = StartupAnimationViewModel()
        state.initialize(eligible = true, animationsEnabled = true)
        state.onFrame(1_000_000_000L)
        state.onFrame(4_000_000_000L)
        assertTrue(state.visible)
        assertFalse(state.hasStarted)
        assertEquals(0f, state.progress, 0f)
        state.markReady()
        state.awaitWindow()
        state.onFrame(5_000_000_000L)
        assertFalse(state.hasStarted)
        state.markReady()
        state.onFrame(6_000_000_000L)
        state.onFrame(6_440_000_000L)
        assertEquals(.5f, state.progress, .001f)
        state.awaitWindow()
        assertTrue("已开始的重建保留原有时间轴", state.ready)
    }
}
