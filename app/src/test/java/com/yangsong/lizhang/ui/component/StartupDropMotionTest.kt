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
        val landing = frame(260f)
        assertEquals(0f, landing.catOffsetYDp, 0f)
        assertEquals(1f, landing.catScaleX, 0f)
        assertEquals(1f, landing.catScaleY, 0f)
        val squash = frame(280f)
        assertEquals(1.06f, squash.catScaleX, .001f)
        assertEquals(.94f, squash.catScaleY, .001f)
        val bounce = frame(320f)
        assertEquals(-12f, bounce.catOffsetYDp, .001f)
        assertEquals(1f, bounce.catScaleX, .001f)
        assertEquals(1f, bounce.catScaleY, .001f)
        assertEquals(0f, frame(380f).catOffsetYDp, 0f)
    }

    @Test fun 下落加速且到落地点不越界() {
        val earlyDistance = frame(65f).catOffsetYDp - frame(0f).catOffsetYDp
        val lateDistance = frame(260f).catOffsetYDp - frame(195f).catOffsetYDp
        assertTrue("相同时间段内接近落地时位移更大", lateDistance > earlyDistance)
        var previous = frame(0f).catOffsetYDp
        for (time in 1..260) {
            val current = frame(time.toFloat()).catOffsetYDp
            assertTrue(current >= previous && current <= 0f)
            previous = current
        }
    }

    @Test fun 落地前纯白三个涟漪错开且揭示四角后才淡出() {
        assertEquals(0f, frame(259f).revealRadiusDp, 0f)
        assertTrue(frame(259f).ripples.all { it.opacity == 0f })
        assertEquals(.65f, frame(260f).ripples[0].opacity, 0f)
        assertEquals(0f, frame(309f).ripples[1].opacity, 0f)
        assertEquals(.50f, frame(310f).ripples[1].opacity, 0f)
        assertEquals(0f, frame(359f).ripples[2].opacity, 0f)
        assertEquals(.35f, frame(360f).ripples[2].opacity, 0f)
        for ((width, height) in listOf(390f to 844f, 360f to 800f, 844f to 390f, 600f to 1024f)) {
            val end = StartupDropMotion.frameAt(620f, width, height)
            assertEquals("圆揭示抵达四角", hypot(width / 2f, height / 2f), end.revealRadiusDp, .001f)
            assertEquals(1f, end.overlayAlpha, 0f)
        }
        assertTrue(frame(760f).overlayAlpha in 0f..1f)
        assertTrue(frame(760f).overlayAlpha < 1f)
        assertEquals(0f, frame(900f).overlayAlpha, 0f)
        assertTrue(frame(720f).ripples.all { it.opacity == 0f })
    }

    @Test fun 系统栏跟随白底和暗色揭示而不改变关闭动画路径() {
        assertTrue(StartupDropMotion.darkSystemBarIcons(0f, 390f, 844f, true, true))
        assertFalse(StartupDropMotion.darkSystemBarIcons(620f, 390f, 844f, true, true))
        assertFalse(StartupDropMotion.darkSystemBarIcons(0f, 390f, 844f, false, true))
        assertTrue(StartupDropMotion.darkSystemBarIcons(900f, 390f, 844f, false, false))
        assertTrue(StartupDropMotion.darkSystemBarIcons(0f, 0f, 0f, true, true))
    }

    private fun frame(elapsedMs: Float) = StartupDropMotion.frameAt(elapsedMs, 390f, 844f)
}
