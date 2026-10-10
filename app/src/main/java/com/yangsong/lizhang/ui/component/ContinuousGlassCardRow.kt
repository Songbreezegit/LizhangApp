package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 惰性行拼成同一张连续卡片，只在整张卡片的首尾保留圆角和横向边线。 */
@Composable
fun Modifier.continuousGlassCardRow(
    index: Int,
    count: Int,
    shapeRadius: Dp = GlassTokens.Radius,
): Modifier {
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) shapeRadius else 0.dp,
        topEnd = if (first) shapeRadius else 0.dp,
        bottomEnd = if (last) shapeRadius else 0.dp,
        bottomStart = if (last) shapeRadius else 0.dp,
    )
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val framed = if (first || last) clip(shape) else this
    return framed.background(glassColor(), shape).drawWithCache {
        val stroke = 1.dp.toPx()
        val inset = stroke / 2f
        val radius = shapeRadius.toPx().coerceAtMost(size.width / 2f)
            .coerceAtMost(if (first && last) size.height / 2f else size.height)
        // 中间行的圆角及横线放到自身可见区域之外，避免逐行描边或增加行间距。
        val top = if (first) inset else -radius * 2f - stroke
        val bottom = if (last) size.height - inset else size.height + radius * 2f + stroke
        onDrawWithContent {
            drawContent()
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(inset, top),
                size = Size((size.width - stroke).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                style = Stroke(stroke),
            )
            drawContext.canvas.restore()
        }
    }
}
