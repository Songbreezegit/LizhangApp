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

/** 三个圆泡从目标端小到云端大；整组保留入场行程，不绘制三角尾或连接线。 */
internal fun guideThoughtBubbles(anchor: Rect, cloud: GuideCloudGeometry, cat: Rect?, safe: Rect,
    cloudAbove: Boolean, unit: Float): List<GuideThoughtBubble> {
    val target = Offset(anchor.center.x, if (cloudAbove) anchor.top else anchor.bottom)
    val inset = min(24f * unit, cloud.body.width / 2f)
    val nearX = target.x.coerceIn(cloud.body.left + inset, cloud.body.right - inset)
    // 猫挡住正对目标的云边时，仅尝试猫左右两侧的通道，不用分散的任意位置。
    val candidates = if (cloudAbove || cat == null) listOf(nearX) else listOf(nearX,
        (cat.left - 24f * unit).coerceIn(cloud.body.left + inset, cloud.body.right - inset),
        (cat.right + 24f * unit).coerceIn(cloud.body.left + inset, cloud.body.right - inset))
        .distinct().sortedBy { kotlin.math.abs(it - target.x) }
    for (radiusScale in listOf(1f, .85f, .65f, .45f)) {
        val largeRadius = 8f * unit * radiusScale
        val middleRadius = 6f * unit * radiusScale
        val smallRadius = 4f * unit * radiusScale
        for (startX in candidates) {
            val source = Offset(startX, cloud.boundaryY(startX, above = !cloudAbove))
            if (cat?.inflate(4f * unit)?.contains(source) == true) continue
            val vector = target - source
            val length = vector.getDistance()
            if (length <= 0f) continue
            val direction = vector / length
            // 三个泡与云朵一起向下入场；目标端留出额外 8dp，避免中间帧擦入高亮。
            val smallDistance = length - smallRadius - 12f * unit
            val minimumGap = 3f * unit
            val maximumLargeDistance = smallDistance - largeRadius - 2f * middleRadius - smallRadius - 2f * minimumGap
            var largeDistance = largeRadius + 4f * unit
            if (maximumLargeDistance < largeDistance) continue
            fun clearsCloud(distance: Float) = cloud.clears(GuideThoughtBubble(source + direction * distance,
                largeRadius + 3.5f * unit), above = !cloudAbove)
            // 云边的侧弧比投影点更突出；只沿同一条通道向目标收进，不另找散乱位置。
            if (!clearsCloud(largeDistance)) {
                if (!clearsCloud(maximumLargeDistance)) continue
                var low = largeDistance
                var high = maximumLargeDistance
                repeat(12) {
                    val middle = (low + high) / 2f
                    if (clearsCloud(middle)) high = middle else low = middle
                }
                largeDistance = high
            }
            largeDistance = guideCatClearDistance(source, direction, cat, largeRadius, largeDistance,
                maximumLargeDistance, largeDistance, unit) ?: continue
            val middleMinimum = largeDistance + largeRadius + middleRadius + minimumGap
            val middleMaximum = smallDistance - smallRadius - middleRadius - minimumGap
            val middleDistance = guideCatClearDistance(source, direction, cat, middleRadius, middleMinimum,
                middleMaximum, (middleMinimum + middleMaximum) / 2f, unit) ?: continue
            val dots = listOf(GuideThoughtBubble(source + direction * smallDistance, smallRadius),
                GuideThoughtBubble(source + direction * middleDistance, middleRadius),
                GuideThoughtBubble(source + direction * largeDistance, largeRadius))
            if (dots.all { dot ->
                val bounds = dot.bounds.inflate(.5f * unit)
                val swept = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom + 8f * unit)
                swept.left >= safe.left && swept.right <= safe.right && swept.top >= safe.top && swept.bottom <= safe.bottom &&
                    !swept.overlaps(anchor) && (cat == null || !bounds.inflate(4f * unit).overlaps(cat)) &&
                    cloud.clears(dot.copy(radius = dot.radius + .5f * unit), above = !cloudAbove)
            }) return dots
        }
    }
    return emptyList()
}

/** 圆泡中心沿既定通道避开猫的扩展矩形，优先保留均匀间距。 */
private fun guideCatClearDistance(source: Offset, direction: Offset, cat: Rect?, radius: Float,
    minimum: Float, maximum: Float, preferred: Float, unit: Float): Float? {
    if (minimum > maximum) return null
    val desired = preferred.coerceIn(minimum, maximum)
    if (cat == null) return desired
    val obstacle = cat.inflate(radius + 4.5f * unit)
    fun interval(position: Float, vector: Float, lower: Float, upper: Float): Pair<Float, Float>? {
        if (kotlin.math.abs(vector) < .0001f) {
            return if (position in lower..upper) Float.NEGATIVE_INFINITY to Float.POSITIVE_INFINITY else null
        }
        val first = (lower - position) / vector
        val last = (upper - position) / vector
        return minOf(first, last) to maxOf(first, last)
    }
    val horizontal = interval(source.x, direction.x, obstacle.left, obstacle.right) ?: return desired
    val vertical = interval(source.y, direction.y, obstacle.top, obstacle.bottom) ?: return desired
    val blockedStart = maxOf(horizontal.first, vertical.first)
    val blockedEnd = minOf(horizontal.second, vertical.second)
    if (blockedStart > blockedEnd || desired < blockedStart || desired > blockedEnd) return desired
    return listOf(blockedStart - .5f * unit, blockedEnd + .5f * unit)
        .filter { it in minimum..maximum }.minByOrNull { kotlin.math.abs(it - desired) }
}
