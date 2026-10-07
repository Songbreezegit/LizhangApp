package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.core.graphics.withSave
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import com.yangsong.lizhang.ui.theme.LocalThemeDarkFraction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 普通内容采用接近实色的表面；透明悬浮层继续由 FrostedGlassTokens 独立管理。 */
object GlassTokens {
    const val LightAlpha = .98f
    const val DarkAlpha = .98f
    const val BorderAlpha = .08f
    const val HighlightAlpha = .06f
    const val FloatingLightAlpha = .46f
    const val FloatingDarkAlpha = .60f
    const val DialogAlpha = 1f
    const val ScrimAlpha = .62f
    const val SelectedAlpha = .55f
    const val DisabledAlpha = .38f
    const val PressedAlpha = .12f
    val Radius = 24.dp
    val ControlRadius = 20.dp
    val FloatingRadius = 30.dp
    val Elevation = 1.dp
    val FloatingElevation = 6.dp
    val BottomClearance = 132.dp
    val ListBottomClearance = 216.dp
}

@Composable fun glassColor(): Color = MaterialTheme.colorScheme.surface.copy(
    alpha = GlassTokens.LightAlpha + (GlassTokens.DarkAlpha - GlassTokens.LightAlpha) * LocalThemeDarkFraction.current,
)

@Composable
fun Modifier.glassFrame(shape: Shape = RoundedCornerShape(GlassTokens.Radius), floating: Boolean = false): Modifier {
    val base = glassColor()
    if (floating) {
        // 兼容已有悬浮表面：仅在轮廓外绘制阴影，透明度和高光参数保持原样。
        val alpha = floatingGlassAlpha()
        return floatingGlassShadow(shape).clip(shape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = alpha), base.copy(alpha = alpha - GlassTokens.HighlightAlpha))))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BorderAlpha)), shape)
    }
    // 内容表面不再叠加反光渐变或内层阴影，让信息、输入和悬浮导航分别形成层级。
    return clip(shape).background(base)
        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
}
@Composable fun GlassSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.glassFrame()) { content() }
}

@Composable fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.Radius),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick == null) modifier else modifier.pressClickable(shape = shape, onClick = onClick)
    Column(clickable.glassFrame(shape), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GlassTokens.ControlRadius),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.DisabledAlpha),
    ),
    content: @Composable RowScope.() -> Unit,
) {
    // 调用方传入的危险操作颜色保留；主要实色按钮不增加多余的内层描边。
    val tonal = colors.containerColor != MaterialTheme.colorScheme.primary && colors.containerColor != MaterialTheme.colorScheme.error
    val source = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Button(onClick, modifier.heightIn(min = 48.dp).pressFeedback(source, shape, enabled,
            accent = colors.contentColor),
            enabled = enabled, shape = shape, colors = colors, interactionSource = source,
            border = if (tonal) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(GlassTokens.ControlRadius)
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    FilledTonalIconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .pressFeedback(source, shape, enabled, pressedScale = .95f), enabled = enabled,
        shape = shape, interactionSource = source,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.DisabledAlpha),
        ), content = content)
    }
}

/** 不使用 Material FAB 的实体 Surface，背景与前景分别绘制。 */
@Composable fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(56.dp)
            .pressClickable(role = Role.Button, shape = RoundedCornerShape(GlassTokens.ControlRadius),
                pressedScale = .95f, onClick = onClick)
            .frostedGlassFrame(hazeState, RoundedCornerShape(GlassTokens.ControlRadius)),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(GlassTokens.ControlRadius)
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    FilterChip(selected, onClick, label, modifier.heightIn(min = 48.dp).pressFeedback(source, shape, accent = accent),
        leadingIcon = leadingIcon, interactionSource = source, shape = shape,
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = accent.copy(alpha = .14f).compositeOver(MaterialTheme.colorScheme.surface),
            selectedLabelColor = accent, selectedLeadingIconColor = accent),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = .26f) else MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable fun GlassSearchBar(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, null) },
        shape = RoundedCornerShape(GlassTokens.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline))
}

@Composable fun GlassDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(onDismissRequest, confirmButton,
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(GlassTokens.FloatingRadius)),
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = RoundedCornerShape(GlassTokens.FloatingRadius), containerColor = glassColor().copy(alpha = GlassTokens.DialogAlpha),
        tonalElevation = 0.dp, properties = properties)
}

/** 弱操作使用同一圆角和按压反馈，危险操作沿用调用方的红色语义。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(GlassTokens.ControlRadius)
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
    TextButton(onClick, modifier.heightIn(min = 48.dp).pressFeedback(source, shape, enabled, colors.contentColor), enabled = enabled,
        shape = shape, interactionSource = source,
        colors = colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent), content = content)
    }
}

/** 保留 Surface 的点击语义与颜色，只统一触点光晕和回弹。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Surface(onClick = onClick, modifier = modifier.pressFeedback(source, shape, enabled, pressedScale = .985f),
            enabled = enabled, shape = shape, color = color, contentColor = contentColor,
            interactionSource = source, content = content)
    }
}

/** 开关与复选框保留原生选中动画，点击反馈与其他控件使用同一触点光晕。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = glassSwitchColors(),
) {
    val source = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Switch(checked, onCheckedChange, modifier.pressFeedback(source,
            // 开关保留原生滑块动画，触摸期间不缩放可拖动控件的坐标空间。
            RoundedCornerShape(GlassTokens.ControlRadius), enabled, pressedScale = 1f),
            enabled = enabled, colors = colors, interactionSource = source)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Checkbox(checked, onCheckedChange, modifier.pressFeedback(source,
            RoundedCornerShape(12.dp), enabled, pressedScale = .96f),
            enabled = enabled, interactionSource = source)
    }
}

/** 开关关闭态使用中性色，避免 Material 默认紫灰混入蓝橘主题。 */
@Composable
fun glassSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .20f),
    checkedThumbColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .25f),
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
)

@Composable
fun floatingGlassAlpha(): Float = GlassTokens.FloatingLightAlpha +
    (GlassTokens.FloatingDarkAlpha - GlassTokens.FloatingLightAlpha) * LocalThemeDarkFraction.current

/** 阴影只在容器轮廓外绘制，不给半透明玻璃增加灰色内层底色。 */
internal fun Modifier.floatingGlassShadow(shape: Shape): Modifier = this.then(
    Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = androidx.compose.ui.graphics.Path().apply {
            when (outline) {
                is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
                is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
                is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
            }
        }.asAndroidPath()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            setShadowLayer(GlassTokens.FloatingElevation.toPx(), 0f, 2.dp.toPx(), 0x22000000)
        }
        onDrawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.withSave {
                clipOutPath(path)
                drawPath(path, paint)
            }
        }
    },
)
