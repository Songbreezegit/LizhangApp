package com.yangsong.lizhang.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    diagnosticName: String? = null,
): Modifier {
    var interactions by remember(interactionSource) { mutableStateOf(PressFeedbackInteractions()) }
    val currentDiagnosticName by rememberUpdatedState(diagnosticName)
    LaunchedEffect(interactionSource) {
        val presses = mutableListOf<PressInteraction.Press>()
        val focuses = mutableListOf<FocusInteraction.Focus>()
        val hovers = mutableListOf<HoverInteraction.Enter>()
        var operation: com.yangsong.lizhang.core.common.ThemeOperationDiagnostics.Operation? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    presses.add(interaction)
                    interactions = interactions.copy(
                        pressed = true,
                        origin = interaction.pressPosition,
                        hasInteracted = true,
                    )
                }
                is PressInteraction.Release -> {
                    presses.remove(interaction.press)
                    interactions = interactions.copy(pressed = presses.isNotEmpty())
                }
                is PressInteraction.Cancel -> {
                    presses.remove(interaction.press)
                    interactions = interactions.copy(pressed = presses.isNotEmpty())
                }
                is FocusInteraction.Focus -> {
                    focuses.add(interaction)
                    interactions = interactions.copy(focused = true, hasInteracted = true)
                }
                is FocusInteraction.Unfocus -> {
                    focuses.remove(interaction.focus)
                    interactions = interactions.copy(focused = focuses.isNotEmpty())
                }
                is HoverInteraction.Enter -> {
                    hovers.add(interaction)
                    interactions = interactions.copy(hovered = true, hasInteracted = true)
                }
                is HoverInteraction.Exit -> {
                    hovers.remove(interaction.enter)
                    interactions = interactions.copy(hovered = hovers.isNotEmpty())
                }
            }
            val name = currentDiagnosticName
            if (name != null) {
                if (interaction is PressInteraction.Press) operation =
                    com.yangsong.lizhang.core.common.ThemeOperationDiagnostics.observedOperation
                val stage = when (interaction) {
                    is PressInteraction.Press -> "Press"
                    is PressInteraction.Release -> "Release"
                    is PressInteraction.Cancel -> "Cancel"
                    else -> null
                }
                if (stage != null) com.yangsong.lizhang.core.common.ThemeOperationDiagnostics.record(
                    "interaction.$name.$stage", operation)
            }
        }
    }
    // 日历等密集控件只收集一次交互，第一次交互前不创建动画与光晕路径。
    val animation = if (interactions.hasInteracted) rememberPressFeedbackAnimation(
        interactionSource = interactionSource,
        active = enabled && interactions.pressed,
        hovered = enabled && interactions.hovered,
        focused = enabled && interactions.focused,
        pressedScale = pressedScale,
    ) else null
    val focused = interactions.focused
    val origin = interactions.origin
    return graphicsLayer {
        val scale = if (enabled) animation?.scale?.value ?: 1f else 1f
        scaleX = scale
        scaleY = scale
    }.drawWithCache {
        if (animation == null) return@drawWithCache onDrawWithContent { drawContent() }
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
            val light = animation.light.value
            val spread = animation.spread.value
            val focus = animation.focus.value
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

private data class PressFeedbackInteractions(
    val pressed: Boolean = false,
    val focused: Boolean = false,
    val hovered: Boolean = false,
    val origin: Offset = Offset.Unspecified,
    val hasInteracted: Boolean = false,
)

private class PressFeedbackAnimation {
    val scale = Animatable(1f)
    val light = Animatable(0f)
    val spread = Animatable(0f)
    val focus = Animatable(0f)
}

@Composable
private fun rememberPressFeedbackAnimation(
    interactionSource: InteractionSource,
    active: Boolean,
    hovered: Boolean,
    focused: Boolean,
    pressedScale: Float,
): PressFeedbackAnimation {
    val animation = remember(interactionSource) { PressFeedbackAnimation() }
    LaunchedEffect(animation, active, pressedScale) {
        animation.scale.animateTo(
            if (active) pressedScale else 1f,
            if (active) tween(90) else spring(dampingRatio = .72f, stiffness = 520f),
        )
    }
    LaunchedEffect(animation, active, hovered) {
        animation.light.animateTo(if (active) 1f else if (hovered) .32f else 0f, tween(if (active) 110 else 180))
    }
    LaunchedEffect(animation, active) {
        animation.spread.animateTo(if (active) 1f else 0f, tween(260))
    }
    LaunchedEffect(animation, focused) {
        animation.focus.animateTo(if (focused) 1f else 0f, tween(120))
    }
    return animation
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
    diagnosticName: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressFeedback(source, shape, enabled, accent, pressedScale, diagnosticName)
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
