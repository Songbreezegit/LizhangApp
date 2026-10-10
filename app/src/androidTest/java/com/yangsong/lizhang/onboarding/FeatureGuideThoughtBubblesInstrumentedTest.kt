package com.yangsong.lizhang.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.ui.onboarding.GuideCloudGeometry
import com.yangsong.lizhang.ui.onboarding.GuideThoughtBubble
import com.yangsong.lizhang.ui.onboarding.guideCloudGeometry
import com.yangsong.lizhang.ui.onboarding.guideThoughtBubbles
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 原生 Path 参与云形合并，在 Android 上复核真实轮廓与完整入场行程。 */
@RunWith(AndroidJUnit4::class)
class FeatureGuideThoughtBubblesInstrumentedTest {
    @Test fun 首页导航的点击区域不能截断指向真实图标的三个泡() {
        val clickTop = 680f
        // 88dp 点击区含 24dp 顶部装饰槽，23dp 图标在余下区域居中。
        val anchor = Rect(260f, clickTop + 44.5f, 283f, clickTop + 67.5f).inflate(6f)
        val cloud = guideCloudGeometry(Rect(16f, 446.5f, 344f, anchor.top - 64f), 1f)
        val cat = Rect(121.4667f, 406.5f, 249.4667f, 470.5f)
        val systemSafe = Rect(16f, 24f, 344f, 788f)
        val dots = assertThreeThroughoutEntrance(anchor, cloud, cat, systemSafe, cloudAbove = true)
        assertTrue("云体继续避开导航完整点击区", cloud.body.bottom <= clickTop - 12f)
        assertTrue("圆泡经过导航留白后才能靠近视觉锚点", dots.any { it.bounds.bottom > clickTop })
        assertTrue("旧云体边界会错误省略全部圆泡", guideThoughtBubbles(anchor, cloud, cat,
            Rect(systemSafe.left, systemSafe.top, systemSafe.right, clickTop - 12f), true, 1f).isEmpty())
    }

    @Test fun 金额高亮下方的三个泡沿猫侧通道避开完整猫咪() {
        for (unit in listOf(1f, 2.75f)) {
            val anchor = scaledRect(30f, 260f, 330f, 320f, unit)
            val cloud = guideCloudGeometry(scaledRect(16f, 424f, 344f, 632f, unit), unit)
            val cat = scaledRect(121.4667f, 384f, 249.4667f, 448f, unit)
            val safe = scaledRect(16f, 24f, 344f, 788f, unit)
            val dots = assertThreeThroughoutEntrance(anchor, cloud, cat, safe, cloudAbove = false, unit = unit)
            assertTrue("大泡从猫侧贴近云端，不能改成只指向猫顶", dots.last().center.x < cat.left ||
                dots.last().center.x > cat.right)
            assertEquals("正常金额场景保留 8dp 大泡", 8f * unit, dots.last().radius, .01f)
        }
    }

    @Test fun 窄屏顶部及底部左右目标均保留三个泡和完整入场边界() {
        val safe = Rect(16f, 24f, 304f, 628f)
        val catAbove = Rect(100.8f, 240f, 228.8f, 304f)
        val upperCloud = guideCloudGeometry(Rect(16f, 280f, 304f, 488f), 1f)
        for (anchor in listOf(Rect(21f, 552f, 64f, 591f), Rect(256f, 552f, 299f, 591f))) {
            assertThreeThroughoutEntrance(anchor, upperCloud, catAbove, safe, cloudAbove = true)
        }
        val catBelow = Rect(100.8f, 168f, 228.8f, 232f)
        val lowerCloud = guideCloudGeometry(Rect(16f, 208f, 304f, 416f), 1f)
        // RTL 使用物理锚点；猫咪保持原物理位置，不再额外镜像。
        for (anchor in listOf(Rect(248f, 56f, 294f, 104f), Rect(26f, 56f, 72f, 104f))) {
            assertThreeThroughoutEntrance(anchor, lowerCloud, catBelow, safe, cloudAbove = false)
        }
    }

    @Test fun 受限间距只能共同缩放三个泡不能单独漏泡() {
        val anchor = Rect(100f, 400f, 140f, 440f)
        val cloud = guideCloudGeometry(Rect(16f, 148f, 344f, 356f), 1f)
        val safe = Rect(16f, 24f, 344f, 788f)
        val dots = assertThreeThroughoutEntrance(anchor, cloud, null, safe, cloudAbove = true)
        assertTrue("受限间距确实触发共同缩放", dots.last().radius < 8f)
        assertEquals("小中大保持相同比例", dots.first().radius / 4f, dots[1].radius / 6f, .001f)
        assertEquals("小中大保持相同比例", dots.first().radius / 4f, dots.last().radius / 8f, .001f)
        assertTrue("无法容纳整组时全部省略", guideThoughtBubbles(anchor, cloud, null,
            Rect(16f, 24f, 344f, 300f), true, 1f).isEmpty())
    }

    private fun assertThreeThroughoutEntrance(anchor: Rect, cloud: GuideCloudGeometry, cat: Rect?, safe: Rect,
        cloudAbove: Boolean, unit: Float = 1f): List<GuideThoughtBubble> {
        val dots = guideThoughtBubbles(anchor, cloud, cat, safe, cloudAbove, unit)
        assertEquals("任一可用通道必须返回完整三个圆泡", 3, dots.size)
        val target = Offset(anchor.center.x, if (cloudAbove) anchor.top else anchor.bottom)
        dots.zipWithNext().forEach { (small, large) ->
            assertTrue("从目标到云端半径严格递增", small.radius < large.radius)
            assertTrue("较大圆泡靠近云端", (small.center - target).getDistance() < (large.center - target).getDistance())
            assertTrue("两段圆泡间隔至少 3dp", (large.center - small.center).getDistance() - large.radius - small.radius >=
                3f * unit - .01f)
        }
        for (travel in listOf(0f, 2f, 4f, 8f)) {
            val movement = Offset(0f, travel * unit)
            val movedCloud = cloud.copy(body = cloud.body.translate(movement), safeRect = cloud.safeRect.translate(movement),
                lobes = cloud.lobes.map { it.translate(movement) })
            dots.forEach { dot ->
                val moved = dot.copy(center = dot.center + movement)
                val bounds = moved.bounds.inflate(.5f * unit)
                assertTrue("完整入场行程仍位于系统安全范围", bounds.left >= safe.left && bounds.right <= safe.right &&
                    bounds.top >= safe.top && bounds.bottom <= safe.bottom)
                assertFalse("圆泡边框不擦入真实高亮", bounds.overlaps(anchor))
                if (cat != null) assertFalse("圆泡不遮猫咪", bounds.inflate(4f * unit).overlaps(cat.translate(movement)))
                assertTrue("圆泡与真实云边分离", movedCloud.clears(moved.copy(radius = moved.radius + .5f * unit),
                    above = !cloudAbove))
            }
        }
        return dots
    }

    private fun scaledRect(left: Float, top: Float, right: Float, bottom: Float, unit: Float) =
        Rect(left * unit, top * unit, right * unit, bottom * unit)
}
