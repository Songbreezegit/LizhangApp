package com.yangsong.lizhang.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.min

/** 云朵保持浅暖白身份；颜色只在引导内部使用，不改变深色页面的主题。 */
internal object GuideCloudPalette {
    val surface = Color(0xFFFFFCF7)
    val outline = Color(0xFFC8BFB4)
    val title = Color(0xFF292E35)
    val body = Color(0xFF5D656E)
    val action = Color(0xFFA44625)
    val onAction = Color.White
}

/** 四边采用不等宽的宽缓凸起，尾根切入真实云边，整个主体只闭合一次。 */
internal fun guideBubblePath(
    body: Rect,
    pointer: GuidePointer,
    cornerRadius: Float,
    rootCurve: Float,
    tipRadius: Float,
): Path {
    val radius = min(cornerRadius, min(body.width, body.height) / 2f).coerceAtLeast(0f)
    val unit = radius / 24f
    val edgeInset = 8f * unit
    val top = cloudEdge(Offset(body.left + radius, body.top), Offset(body.right - radius, body.top),
        Offset(0f, unit), floatArrayOf(.18f, .30f, .28f, .24f),
        floatArrayOf(8f, 10f, 8f, 9f, 8f), floatArrayOf(1f, 0f, 2f, 0f))
    val right = cloudEdge(Offset(body.right, body.top + radius), Offset(body.right, body.bottom - radius),
        Offset(-unit, 0f), floatArrayOf(.28f, .40f, .32f),
        floatArrayOf(8f, 10f, 8f, 8f), floatArrayOf(0f, 3f, 1f))
    val bottom = cloudEdge(Offset(body.right - radius, body.bottom), Offset(body.left + radius, body.bottom),
        Offset(0f, -unit), floatArrayOf(.23f, .27f, .19f, .31f),
        floatArrayOf(8f, 9f, 10f, 8f, 8f), floatArrayOf(2f, 0f, 1f, 0f))
    val left = cloudEdge(Offset(body.left, body.bottom - radius), Offset(body.left, body.top + radius),
        Offset(unit, 0f), floatArrayOf(.34f, .27f, .39f),
        floatArrayOf(8f, 9f, 10f, 8f), floatArrayOf(1f, 0f, 2f))
    val tailOnTop = pointer.baseLeft.y == body.top
    return Path().apply {
        moveTo(top.first().start.x, top.first().start.y)
        appendCloudEdge(top, if (tailOnTop) pointer else null, rootCurve, tipRadius)
        cubicTo(body.right - radius + 10f * unit, body.top + edgeInset,
            body.right - edgeInset, body.top + radius - 10f * unit,
            right.first().start.x, right.first().start.y)
        appendCloudEdge(right)
        cubicTo(body.right - edgeInset, body.bottom - radius + 10f * unit,
            body.right - radius + 10f * unit, body.bottom - edgeInset,
            bottom.first().start.x, bottom.first().start.y)
        appendCloudEdge(bottom, if (!tailOnTop) pointer else null, rootCurve, tipRadius)
        cubicTo(body.left + radius - 10f * unit, body.bottom - edgeInset,
            body.left + edgeInset, body.bottom - radius + 10f * unit,
            left.first().start.x, left.first().start.y)
        appendCloudEdge(left)
        cubicTo(body.left + edgeInset, body.top + radius - 10f * unit,
            body.left + radius - 10f * unit, body.top + edgeInset,
            top.first().start.x, top.first().start.y)
        close()
    }
}

private data class CloudCurve(val start: Offset, val control1: Offset, val control2: Offset, val end: Offset) {
    fun appendTo(path: Path) = path.cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
    fun split(t: Float): Pair<CloudCurve, CloudCurve> {
        val first = interpolate(start, control1, t)
        val middle = interpolate(control1, control2, t)
        val last = interpolate(control2, end, t)
        val before = interpolate(first, middle, t)
        val after = interpolate(middle, last, t)
        val point = interpolate(before, after, t)
        return CloudCurve(start, first, before, point) to CloudCurve(point, after, last, end)
    }
    fun atX(x: Float): Float {
        val increasing = end.x >= start.x
        var low = 0f
        var high = 1f
        repeat(24) {
            val t = (low + high) / 2f
            val inverse = 1f - t
            val position = start.x * inverse * inverse * inverse +
                3f * control1.x * inverse * inverse * t + 3f * control2.x * inverse * t * t + end.x * t * t * t
            if ((position < x) == increasing) low = t else high = t
        }
        return (low + high) / 2f
    }
    fun tangent(t: Float): Offset {
        val inverse = 1f - t
        val vector = (control1 - start) * (inverse * inverse) +
            (control2 - control1) * (2f * inverse * t) + (end - control2) * (t * t)
        val length = vector.getDistance()
        return if (length > 0f) vector / length else Offset.Zero
    }
}

private fun interpolate(from: Offset, to: Offset, fraction: Float) = from + (to - from) * fraction

private fun cloudEdge(start: Offset, end: Offset, inward: Offset, widths: FloatArray,
    valleys: FloatArray, crests: FloatArray): List<CloudCurve> {
    val curves = mutableListOf<CloudCurve>()
    val span = end - start
    var fraction = 0f
    widths.forEachIndexed { index, width ->
        val before = start + span * fraction + inward * valleys[index]
        val crest = start + span * (fraction + width / 2f) + inward * crests[index]
        val after = start + span * (fraction + width) + inward * valleys[index + 1]
        val control = span * (width * .20f)
        curves += CloudCurve(before, before + control, crest - control, crest)
        curves += CloudCurve(crest, crest + control, after - control, after)
        fraction += width
    }
    return curves
}

private fun Path.appendCloudEdge(edge: List<CloudCurve>, pointer: GuidePointer? = null,
    rootCurve: Float = 0f, tipRadius: Float = 0f) {
    if (pointer == null) {
        edge.forEach { it.appendTo(this) }
        return
    }
    val increasing = edge.last().end.x >= edge.first().start.x
    val direction = if (increasing) 1f else -1f
    val firstX = if (increasing) pointer.baseLeft.x else pointer.baseRight.x
    val lastX = if (increasing) pointer.baseRight.x else pointer.baseLeft.x
    fun curveAt(x: Float) = edge.first { x >= minOf(it.start.x, it.end.x) - .01f &&
        x <= maxOf(it.start.x, it.end.x) + .01f }
    val firstCurve = curveAt(firstX)
    val lastCurve = curveAt(lastX)
    val firstT = firstCurve.atX(firstX)
    val lastT = lastCurve.atX(lastX)
    val firstPoint = firstCurve.split(firstT).first.end
    val lastPoint = lastCurve.split(lastT).first.end
    appendCloudPortion(edge, edge.first().start.x, firstX, direction)
    val outward = if (increasing) -1f else 1f
    val root = min(rootCurve, abs(lastX - firstX) / 2f)
    val cap = min(tipRadius, min(abs(pointer.tip.y - firstPoint.y), abs(pointer.tip.y - lastPoint.y)) / 3f)
    val capBefore = Offset(pointer.tip.x - direction * cap, pointer.tip.y - outward * cap)
    val capAfter = Offset(pointer.tip.x + direction * cap, pointer.tip.y - outward * cap)
    val firstControl = firstPoint + firstCurve.tangent(firstT) * root
    val lastControl = lastPoint - lastCurve.tangent(lastT) * root
    cubicTo(firstControl.x, firstControl.y, capBefore.x, pointer.tip.y - outward * (cap + root / 2f), capBefore.x, capBefore.y)
    quadraticBezierTo(capBefore.x, pointer.tip.y, pointer.tip.x, pointer.tip.y)
    quadraticBezierTo(capAfter.x, pointer.tip.y, capAfter.x, capAfter.y)
    cubicTo(capAfter.x, pointer.tip.y - outward * (cap + root / 2f), lastControl.x, lastControl.y, lastPoint.x, lastPoint.y)
    appendCloudPortion(edge, lastX, edge.last().end.x, direction)
}

/** 用德卡斯特里奥分割保留云边原曲线；只移除尾根之间的边，不补穿过尾部的横线。 */
private fun Path.appendCloudPortion(edge: List<CloudCurve>, fromX: Float, toX: Float, direction: Float) {
    edge.forEach { curve ->
        val start = maxOf(curve.start.x * direction, fromX * direction)
        val end = minOf(curve.end.x * direction, toX * direction)
        if (end - start > .001f) {
            val firstT = curve.atX(start * direction)
            val lastT = curve.atX(end * direction)
            val beforeEnd = curve.split(lastT).first
            beforeEnd.split((firstT / lastT).coerceIn(0f, 1f)).second.appendTo(this)
        }
    }
}
