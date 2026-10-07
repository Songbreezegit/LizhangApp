package com.yangsong.lizhang.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class FeatureGuideGeometryTest {
    @Test fun `气泡靠右夹持时箭头仍落在目标上边中心`() {
        val anchor = Rect(286f, 700f, 342f, 756f)
        val bubble = Rect(20f, 440f, 340f, 672f)
        val pointer = guidePointer(anchor, bubble, cornerRadius = 24f, halfWidth = 6f)
        assertEquals(Offset(314f, 700f), pointer.tip)
        assertTrue(pointer.baseRight.x <= bubble.right - 24f)
        assertTrue(pointer.baseLeft.x > bubble.left + 24f)
        assertEquals(bubble.bottom, pointer.baseLeft.y)
    }

    @Test fun `气泡靠左和RTL目标使用相同物理坐标`() {
        val anchor = Rect(12f, 700f, 68f, 756f)
        val bubble = Rect(12f, 440f, 332f, 672f)
        val pointer = guidePointer(anchor, bubble, cornerRadius = 24f, halfWidth = 6f)
        assertEquals(Offset(40f, 700f), pointer.tip)
        assertTrue(pointer.baseLeft.x >= bubble.left + 24f)
        assertTrue(pointer.baseRight.x < bubble.right - 24f)
    }

    @Test fun `顶部提醒气泡指回目标下边而不是导航栏`() {
        val anchor = Rect(282f, 32f, 338f, 88f)
        val bubble = Rect(16f, 100f, 344f, 312f)
        val pointer = guidePointer(anchor, bubble, cornerRadius = 24f, halfWidth = 6f)
        assertEquals(Offset(310f, 88f), pointer.tip)
        assertEquals(bubble.top, pointer.baseLeft.y)
        assertTrue(pointer.tip.y < pointer.baseLeft.y)
    }

    @Test fun `导航点击避让矩形与视觉高亮矩形独立保存`() {
        val registry = FeatureGuideTargetRegistry()
        val target = FeatureGuideTarget.ADD_RECORD
        val clickOwner = Any()
        val visualOwner = Any()
        val click = Rect(160f, 680f, 240f, 768f)
        val visual = Rect(172f, 706f, 228f, 758f)
        registry.register(target, clickOwner, click, visual = false)
        registry.register(target, visualOwner, visual)
        assertEquals(click, registry.interactionBounds(target))
        assertEquals(visual, registry.bounds(target))
        registry.unregister(target, clickOwner, visual = false)
        assertNull(registry.interactionBounds(target))
        assertEquals(visual, registry.bounds(target))
    }

    @Test fun `退出组件不能清掉重建后的视觉锚点`() {
        val registry = FeatureGuideTargetRegistry()
        val target = FeatureGuideTarget.CONTACTS
        val previous = Any()
        val current = Any()
        val anchor = Rect(80f, 700f, 140f, 756f)
        registry.register(target, previous, Rect.Zero)
        registry.register(target, current, anchor)
        registry.unregister(target, previous)
        assertEquals(anchor, registry.bounds(target))
    }
}
