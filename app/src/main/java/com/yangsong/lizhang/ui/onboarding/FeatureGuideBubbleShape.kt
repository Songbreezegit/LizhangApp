package com.yangsong.lizhang.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.min
import kotlin.math.sqrt

/** 云朵保持浅暖白身份；颜色只在引导内部使用，不改变深色页面的主题。 */
internal object GuideCloudPalette {
    val surface = Color(0xFFFFFCF7)
    val outline = Color(0xFFC8BFB4)
    val title = Color(0xFF292E35)
    val body = Color(0xFF5D656E)
    val action = Color(0xFFA44625)
    val onAction = Color.White
}

/** 文字安全区和圆团轮廓分别测量；椭圆只参与合并，不绘制各自的内部边框。 */
internal data class GuideCloudGeometry(val body: Rect, val safeRect: Rect, val lobes: List<Rect>, val path: Path) {
    fun boundaryY(x: Float, above: Boolean): Float {
        val edges = lobes.mapNotNull { lobe ->
            val radiusX = lobe.width / 2f
            if (radiusX <= 0f || x < lobe.left || x > lobe.right) null
            else {
                val fraction = ((x - lobe.center.x) / radiusX).coerceIn(-1f, 1f)
                val height = lobe.height / 2f * sqrt((1f - fraction * fraction).coerceAtLeast(0f))
                lobe.center.y + if (above) -height else height
            }
        }.toMutableList()
        if (x in safeRect.left..safeRect.right) edges += if (above) safeRect.top else safeRect.bottom
        return if (above) edges.minOrNull() ?: body.top else edges.maxOrNull() ?: body.bottom
    }

    /** 保守检查整个圆的包围盒，圆泡不会擦入云团外弧。 */
    fun clears(circle: GuideThoughtBubble, above: Boolean): Boolean {
        val rangeLeft = circle.center.x - circle.radius
        val rangeRight = circle.center.x + circle.radius
        val edges = lobes.filter { it.right >= rangeLeft && it.left <= rangeRight }.map { lobe ->
            boundaryForLobe(lobe, lobe.center.x.coerceIn(rangeLeft, rangeRight), above)
        }.toMutableList()
        if (rangeRight >= safeRect.left && rangeLeft <= safeRect.right) edges += if (above) safeRect.top else safeRect.bottom
        val boundary = if (above) edges.minOrNull() ?: body.top else edges.maxOrNull() ?: body.bottom
        return if (above) circle.center.y + circle.radius <= boundary else circle.center.y - circle.radius >= boundary
    }
}

private fun boundaryForLobe(lobe: Rect, x: Float, above: Boolean): Float {
    val fraction = ((x - lobe.center.x) / (lobe.width / 2f)).coerceIn(-1f, 1f)
    val height = lobe.height / 2f * sqrt((1f - fraction * fraction).coerceAtLeast(0f))
    return lobe.center.y + if (above) -height else height
}

/** 三颗明显的顶部大圆团、两侧大弧和三颗底团组成经典云朵，不再沿四边铺小波。 */
internal fun guideCloudGeometry(body: Rect, unit: Float): GuideCloudGeometry {
    val scaleX = body.width / 360f
    // 只有极小的可用高度才压缩云团留白；普通短文仍按至少 208dp 自然测量。
    val scaleY = min(unit, body.height / 160f).coerceAtLeast(0f)
    val height = body.height / scaleY.coerceAtLeast(.001f)
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Rect(
        body.left + (cx - rx) * scaleX, body.top + (cy - ry) * scaleY,
        body.left + (cx + rx) * scaleX, body.top + (cy + ry) * scaleY,
    )
    val lobes = listOf(
        oval(88f, 80f, 66f, 54f), oval(186f, 68f, 82f, 68f), oval(280f, 80f, 60f, 52f),
        oval(58f, (height + 48f) / 2f, 58f, (height - 76f) / 2f),
        oval(304f, (height + 36f) / 2f, 56f, (height - 68f) / 2f),
        oval(94f, height - 46f, 69f, 42f), oval(186f, height - 47f, 82f, 47f),
        oval(278f, height - 48f, 66f, 42f),
    )
    val safe = Rect(body.left + 36f * scaleX, body.top + 48f * scaleY,
        body.right - 36f * scaleX, body.bottom - 28f * scaleY)
    var contour = Path().apply { addRect(safe) }
    lobes.forEach { lobe ->
        contour = Path.combine(PathOperation.Union, contour, Path().apply { addOval(lobe) })
    }
    return GuideCloudGeometry(body, safe, lobes, contour)
}

internal data class GuideThoughtBubble(val center: Offset, val radius: Float) {
    val bounds: Rect get() = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
}

/** 两个独立圆泡沿真实云边到目标相邻边的方向排列；没有三角尾或连接线。 */
internal fun guideThoughtBubbles(anchor: Rect, cloud: GuideCloudGeometry, cat: Rect?, safe: Rect,
    cloudAbove: Boolean, unit: Float): List<GuideThoughtBubble> {
    val target = Offset(anchor.center.x, if (cloudAbove) anchor.top else anchor.bottom)
    val inset = 24f * unit
    val nearX = target.x.coerceIn(cloud.body.left + min(inset, cloud.body.width / 2f),
        cloud.body.right - min(inset, cloud.body.width / 2f))
    val candidates = if (cloudAbove) listOf(nearX) else listOf(nearX,
        cloud.body.left + cloud.body.width * .18f, cloud.body.right - cloud.body.width * .18f,
        cloud.body.left + cloud.body.width * .08f, cloud.body.right - cloud.body.width * .08f)
        .distinct().sortedBy { kotlin.math.abs(it - target.x) }
    for (radiusScale in listOf(1f, .85f, .65f, .45f)) {
        val largeRadius = 8f * unit * radiusScale
        val smallRadius = 4.5f * unit * radiusScale
        for (startX in candidates) {
            val source = Offset(startX, cloud.boundaryY(startX, above = !cloudAbove))
            val vector = target - source
            val length = vector.getDistance()
            if (length <= largeRadius + smallRadius + 8f * unit) continue
            val direction = vector / length
            val largeDistance = if (cloudAbove) largeRadius + 4f * unit else maxOf(largeRadius + 4f * unit, length * .30f)
            val smallDistance = if (cloudAbove) length - smallRadius - 4.5f * unit
                else minOf(length - smallRadius - 4.5f * unit, length * .75f)
            if (smallDistance - largeDistance < largeRadius + smallRadius + 3f * unit) continue
            val dots = listOf(GuideThoughtBubble(source + direction * largeDistance, largeRadius),
                GuideThoughtBubble(source + direction * smallDistance, smallRadius))
            if (dots.all { dot ->
                val bounds = dot.bounds.inflate(.5f * unit)
                bounds.left >= safe.left && bounds.right <= safe.right && bounds.top >= safe.top && bounds.bottom <= safe.bottom &&
                    !bounds.overlaps(anchor) && (cat == null || !bounds.inflate(4f * unit).overlaps(cat)) &&
                    cloud.clears(dot.copy(radius = dot.radius + .5f * unit), above = !cloudAbove)
            }) return dots
        }
    }
    return emptyList()
}
