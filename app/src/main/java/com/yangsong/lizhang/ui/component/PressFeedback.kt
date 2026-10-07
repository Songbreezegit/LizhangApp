package com.yangsong.lizhang.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * 与标准点击共享交互事件：光晕从触点扩散，内容轻压后回弹；滚动取消和禁用都自动复位。
 * 键盘焦点保留主题色轮廓，不添加额外手势识别器，也不改变点击和选择语义。
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: InteractionSource,
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    pressedScale: Float = .975f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var origin by remember(interactionSource) { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) origin = interaction.pressPosition
        }
    }
    val active = enabled && pressed
    val scale by animateFloatAsState(
        if (active) pressedScale else 1f,
        if (active) tween(90) else spring(dampingRatio = .72f, stiffness = 520f),
        label = "控件按压回弹",
    )
    val light by animateFloatAsState(
        if (active) 1f else if (enabled && hovered) .32f else 0f,
        tween(if (active) 110 else 180), label = "触点光晕亮度",
    )
    val spread by animateFloatAsState(if (active) 1f else 0f, tween(260), label = "触点光晕扩散")
    val focus by animateFloatAsState(if (enabled && focused) 1f else 0f, tween(120), label = "键盘焦点轮廓")
    return graphicsLayer {
        scaleX = if (enabled) scale else 1f
        scaleY = if (enabled) scale else 1f
    }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        onDrawWithContent {
            drawContent()
            if (enabled && (light > .001f || focus > .001f)) {
                val touchCenter = if (origin.x.isFinite() && origin.y.isFinite() && !(focused && origin == Offset.Zero)) {
                    Offset(origin.x.coerceIn(0f, size.width), origin.y.coerceIn(0f, size.height))
                } else center
                clipPath(clip) {
                    if (light > .001f) {
                        drawRect(Brush.radialGradient(
                            listOf(accent.copy(alpha = .23f * light), accent.copy(alpha = .09f * light), Color.Transparent),
                            center = touchCenter,
                            radius = (hypot(size.width, size.height) * (.22f + .78f * spread)).coerceAtLeast(1f),
                        ))
                    }
                    drawOutline(outline, accent.copy(alpha = (.38f * light + .72f * focus).coerceAtMost(.9f)),
                        style = Stroke((if (focus > 0f) 2.dp else 1.dp).toPx()))
                }
            }
        }
    }
}

/** 仅替换视觉反馈，继续由 Foundation 处理点击、滚动竞争、键盘和无障碍。 */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
    pressedScale: Float = .985f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source, shape, enabled, accent, pressedScale)
        .clickable(source, indication = null, enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = onClick)
}

@Composable
fun Modifier.pressSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source, shape, enabled, accent, pressedScale = .985f)
        .selectable(selected, source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun Modifier.pressToggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source, shape, enabled, accent, pressedScale = .985f)
        .toggleable(value, source, indication = null, enabled = enabled, role = role, onValueChange = onValueChange)
}
