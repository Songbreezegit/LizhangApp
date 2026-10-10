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
        state.onFrame(1_000_000_000L + durationNanos / 2L)
        val snapshot = state.diagnosticSnapshot()
        assertEquals(3_000L, snapshot.monotonicNanos)
        assertTrue(snapshot.visible && snapshot.ready && snapshot.initialized)
        assertEquals(.5f, snapshot.progress, .001f)
        assertEquals(1_000_000_000L, snapshot.firstFrameNanos)
        val events = state.diagnostics()
        assertEquals(7, events.first { it.event == "内容首帧已提交" }.hostId)
        assertEquals(1_000_000_000L + durationNanos / 2L, events.last().frameNanos)
        assertEquals(.5f, events.last().state.progress, .001f)
        assertTrue(events.zipWithNext().all { (first, second) -> first.state.monotonicNanos <= second.state.monotonicNanos })
    }

    @Test fun Release关闭诊断后不分配逐帧证据而保持固定时长() {
        val state = StartupAnimationViewModel(monotonicClock = { 0L }, diagnosticsEnabled = false)
        assertEquals("保持放缓后的一千三百毫秒设计", 1300L, StartupAnimationViewModel.DURATION_MILLIS)
        state.initialize(eligible = true, animationsEnabled = true)
        state.recordWindowEvent("内容首帧已提交", 7)
        state.markReady()
        state.onFrame(1_000_000_000L)
        state.onFrame(1_900_000_000L)
        assertTrue("原九百毫秒终点尚未播完", state.visible)
        state.onFrame(1_000_000_000L + durationNanos - 1_000_000L)
        assertTrue("结束前一毫秒仍保留启动层", state.visible)
        assertTrue(state.ready)
        state.onFrame(1_000_000_000L + durationNanos)
        assertTrue(state.diagnostics().isEmpty())
        assertFalse(state.visible)
        assertFalse(state.ready)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 配置重建保留时间轴并在固定时长结束() {
        val state = StartupAnimationViewModel(monotonicClock = { 0L })
        state.initialize(eligible = true, animationsEnabled = true)
        state.markReady()
        state.onFrame(1_000_000_000L)
        state.onFrame(1_000_000_000L + durationNanos / 2L)
        assertTrue(state.visible)
        assertEquals(.5f, state.progress, .001f)
        state.initialize(eligible = false, animationsEnabled = true)
        assertTrue(state.visible)
        assertEquals(.5f, state.progress, .001f)
        state.awaitWindow(2)
        assertFalse("新窗口必须重新等自己的提交", state.ready)
        assertTrue("重建保留第一次动画帧", state.hasStarted)
        state.onFrame(1_850_000_000L)
        assertEquals("等待新窗口期间不推进或回拨进度", .5f, state.progress, .001f)
        state.markReady(2)
        state.onFrame(1_000_000_000L + durationNanos - 1_000_000L)
        assertTrue(state.visible)
        state.onFrame(1_000_000_000L + durationNanos)
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
        val state = StartupAnimationViewModel(monotonicClock = { 0L })
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
        state.onFrame(6_000_000_000L + durationNanos / 2L)
        assertEquals(.5f, state.progress, .001f)
        state.awaitWindow()
        assertFalse("已开始的重建仍须等新窗口交接", state.ready)
        assertTrue("等待新窗口时保留原有时间轴", state.hasStarted)
        state.markReady()
        state.onFrame(6_000_000_000L + durationNanos)
        assertFalse(state.visible)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 旧窗口迟到就绪与重复释放不能改变新窗口状态() {
        val state = StartupAnimationViewModel(monotonicClock = { 0L })
        state.initialize(eligible = true, animationsEnabled = true)
        state.awaitWindow(1)
        state.markReady(1)
        state.onFrame(0L)
        state.onFrame(durationNanos / 3L)
        state.releaseWindow(1)
        state.awaitWindow(2)

        state.markReady(1)
        state.releaseWindow(1)
        assertTrue(state.ownsWindow(2))
        assertFalse("旧宿主不能让新窗口提前播放", state.ready)
        assertTrue(state.visible)
        assertEquals(1f / 3f, state.progress, .001f)

        state.markReady(2)
        state.releaseWindow(1)
        assertTrue("旧宿主不能释放新窗口的就绪状态", state.ready)
        state.onFrame(durationNanos)
        assertFalse(state.visible)
        state.releaseWindow(2)
        assertFalse(state.ownsWindow(2))
    }

    @Test fun 等待窗口的绝对截止时间跨重建保持且不足一毫秒向上取整() {
        var now = 0L
        val state = StartupAnimationViewModel(monotonicClock = { now })
        state.initialize(eligible = true, animationsEnabled = true)
        state.awaitWindow(1)
        assertEquals(1500L, state.windowTimeoutRemainingMillis())

        now = 1_000_000_001L
        state.releaseWindow(1)
        state.awaitWindow(2)
        assertEquals("重建不能重新获得完整窗口等待时间", 500L, state.windowTimeoutRemainingMillis())
        now = 1_499_999_999L
        assertEquals(1L, state.windowTimeoutRemainingMillis())
        now = 1_500_000_000L
        assertEquals(0L, state.windowTimeoutRemainingMillis())
        now = 2_000_000_000L
        assertEquals("已过期等待不能变为负数", 0L, state.windowTimeoutRemainingMillis())
    }

    @Test fun 首帧缺失时播放截止时间也生效且重建和重复就绪不延长等待() {
        var now = 0L
        val state = StartupAnimationViewModel(monotonicClock = { now }, diagnosticsEnabled = true)
        state.initialize(eligible = true, animationsEnabled = true)
        state.awaitWindow(1)
        now = 100_000_000L
        state.markReady(1)
        assertEquals(StartupAnimationViewModel.PLAYBACK_TIMEOUT_MILLIS, state.playbackTimeoutRemainingMillis())
        assertFalse(state.hasStarted)

        now = 700_000_000L
        state.releaseWindow(1)
        state.awaitWindow(2)
        assertEquals("新窗口等待不能超过既有播放截止时间", 1300L, state.windowTimeoutRemainingMillis())
        now = 800_000_000L
        state.markReady(2)
        now = 900_000_000L
        state.markReady(2)
        assertEquals("重复交接不能重置截止时间", 1100L, state.playbackTimeoutRemainingMillis())

        now = 100_000_000L + StartupAnimationViewModel.PLAYBACK_TIMEOUT_MILLIS * 1_000_000L
        state.onFrame(now)
        assertFalse("超时任务尚未派发时，迟到首帧也必须释放启动层", state.visible)
        assertFalse(state.ready)
        assertFalse(state.hasStarted)
        assertEquals(1f, state.progress, 0f)
        assertTrue(state.diagnostics().any { it.event == "启动动画截止时间已过" })
    }

    @Test fun 播放暂停后单调时钟到期即释放而不根据恢复帧续播() {
        var now = 0L
        val state = StartupAnimationViewModel(monotonicClock = { now })
        state.initialize(eligible = true, animationsEnabled = true)
        state.markReady()
        state.onFrame(0L)
        now = 200_000_000L
        state.onFrame(now)
        assertTrue(state.visible)

        now = StartupAnimationViewModel.PLAYBACK_TIMEOUT_MILLIS * 1_000_000L
        // 即使恢复帧携带的帧时间较旧，也先按原绝对截止时间释放。
        state.onFrame(400_000_000L)
        assertFalse(state.visible)
        assertFalse(state.ready)
        assertEquals(1f, state.progress, 0f)
    }

    @Test fun 主动结束清空截止时间且释放与迟到回调可重复执行() {
        var now = 0L
        val state = StartupAnimationViewModel(monotonicClock = { now }, diagnosticsEnabled = true)
        state.initialize(eligible = true, animationsEnabled = true)
        state.awaitWindow(7)
        state.markReady(7)
        state.onFrame(0L)
        now = 100_000_000L
        state.finish()
        state.finish()
        assertEquals("重复结束只记录一次资源释放", 1, state.diagnostics().count { it.event == "启动层已释放" })
        assertEquals(0L, state.windowTimeoutRemainingMillis())
        assertEquals(0L, state.playbackTimeoutRemainingMillis())

        state.releaseWindow(7)
        state.releaseWindow(7)
        state.markReady(7)
        state.awaitWindow(8)
        state.onFrame(durationNanos)
        state.initialize(eligible = true, animationsEnabled = true)
        assertFalse(state.ownsWindow(7))
        assertFalse(state.ownsWindow(8))
        assertFalse(state.visible)
        assertFalse(state.ready)
        assertEquals(1f, state.progress, 0f)
    }

    private val durationNanos get() = StartupAnimationViewModel.DURATION_MILLIS * 1_000_000L
}
