package com.yangsong.lizhang.ui.component

import com.yangsong.lizhang.ui.viewmodel.StartupAnimationViewModel
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDropMotionTest {
    @Test fun 参考窗口完全顶外开始并按落地压缩回弹恢复() {
        assertEquals(StartupAnimationViewModel.DURATION_MILLIS.toFloat(), StartupDropMotion.DURATION_MS, 0f)
        val start = frame(0f)
        assertEquals(-64f, 422f + start.catOffsetYDp, .01f)
        assertTrue("起始猫咪底沿必须在屏幕之外", 422f + start.catOffsetYDp + 56f < 0f)
        val landing = frame(StartupDropMotion.LANDING_MS)
        assertEquals(0f, landing.catOffsetYDp, 0f)
        assertEquals(1f, landing.catScaleX, 0f)
        assertEquals(1f, landing.catScaleY, 0f)
        val squash = frame(StartupDropMotion.SQUASH_PEAK_MS)
        assertEquals(1.06f, squash.catScaleX, .001f)
        assertEquals(.94f, squash.catScaleY, .001f)
        val bounce = frame(StartupDropMotion.BOUNCE_PEAK_MS)
        assertEquals(-12f, bounce.catOffsetYDp, .001f)
        assertEquals(1f, bounce.catScaleX, .001f)
        assertEquals(1f, bounce.catScaleY, .001f)
        assertEquals(0f, frame(StartupDropMotion.SETTLE_MS).catOffsetYDp, 0f)
    }

    @Test fun 下落加速且到落地点不越界() {
        val earlyDistance = frame(StartupDropMotion.LANDING_MS / 4f).catOffsetYDp - frame(0f).catOffsetYDp
        val lateDistance = frame(StartupDropMotion.LANDING_MS).catOffsetYDp -
            frame(StartupDropMotion.LANDING_MS * .75f).catOffsetYDp
        assertTrue("相同时间段内接近落地时位移更大", lateDistance > earlyDistance)
        var previous = frame(0f).catOffsetYDp
        for (time in 1..StartupDropMotion.LANDING_MS.toInt()) {
            val current = frame(time.toFloat()).catOffsetYDp
            assertTrue(current >= previous && current <= 0f)
            previous = current
        }
    }

    @Test fun 落地前纯白三个水波错开且揭示四角后才淡出() {
        assertEquals(0f, frame(379f).revealRadiusDp, 0f)
        assertTrue(frame(379f).ripples.all { it.opacity == 0f })
        assertEquals(.65f, frame(380f).ripples[0].opacity, 0f)
        assertEquals(0f, frame(469f).ripples[1].opacity, 0f)
        assertEquals(.50f, frame(470f).ripples[1].opacity, 0f)
        assertEquals(0f, frame(559f).ripples[2].opacity, 0f)
        assertEquals(.35f, frame(560f).ripples[2].opacity, 0f)
        for ((width, height) in listOf(390f to 844f, 360f to 800f, 844f to 390f, 600f to 1024f)) {
            val end = StartupDropMotion.frameAt(940f, width, height)
            assertEquals("圆揭示抵达四角", hypot(width / 2f, height / 2f), end.revealRadiusDp, .001f)
            assertEquals(1f, end.overlayAlpha, 0f)
        }
        assertTrue(frame(1120f).overlayAlpha in 0f..1f)
        assertTrue(frame(1120f).overlayAlpha < 1f)
        assertEquals(0f, frame(1300f).overlayAlpha, 0f)
        assertTrue(frame(1220f).ripples.all { it.opacity == 0f })
    }

    @Test fun 水波保持宽波带向外扩大并各自渐隐不留下实心遮罩() {
        val amplitudes = listOf(.65f, .50f, .35f)
        StartupDropMotion.RIPPLE_STARTS_MS.forEachIndexed { index, start ->
            val first = frame(start).ripples[index]
            val middle = frame(start + StartupDropMotion.RIPPLE_DURATION_MS / 2f).ripples[index]
            val end = frame(start + StartupDropMotion.RIPPLE_DURATION_MS).ripples[index]
            assertEquals(56f, first.radiusDp, .001f)
            assertTrue("水波使用宽波带而不是细描边", first.bandWidthDp >= 18f)
            assertTrue(middle.radiusDp > first.radiusDp)
            assertTrue(middle.bandWidthDp > first.bandWidthDp && middle.bandWidthDp <= 32f)
            assertEquals(amplitudes[index] / 2f, middle.opacity, .001f)
            assertEquals(0f, end.opacity, 0f)
            assertTrue("波带始终保留透明中心", end.radiusDp > end.bandWidthDp / 2f)
        }
    }

    @Test fun 系统栏跟随白底和暗色揭示而不改变关闭动画路径() {
        assertTrue(StartupDropMotion.darkSystemBarIcons(0f, 390f, 844f, true, true))
        assertFalse(StartupDropMotion.darkSystemBarIcons(940f, 390f, 844f, true, true))
        assertFalse(StartupDropMotion.darkSystemBarIcons(0f, 390f, 844f, false, true))
        assertTrue(StartupDropMotion.darkSystemBarIcons(1300f, 390f, 844f, false, false))
        assertTrue(StartupDropMotion.darkSystemBarIcons(0f, 0f, 0f, true, true))
    }

    private fun frame(elapsedMs: Float) = StartupDropMotion.frameAt(elapsedMs, 390f, 844f)
}
