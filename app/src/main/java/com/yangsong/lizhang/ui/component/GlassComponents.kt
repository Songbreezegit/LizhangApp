package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.core.graphics.withSave
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 统一的轻玻璃参数；不采样背景、不创建模糊图层，适用于长列表和旧设备。 */
object GlassTokens {
    const val LightAlpha = .42f
    const val DarkAlpha = .62f
    const val BorderAlpha = .08f
    const val HighlightAlpha = .06f
    const val FloatingLightAlpha = .46f
    const val FloatingDarkAlpha = .60f
    const val DialogAlpha = .94f
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
    alpha = if (MaterialTheme.colorScheme.background.luminance() < .5f) GlassTokens.DarkAlpha else GlassTokens.LightAlpha,
)

@Composable
fun Modifier.glassFrame(shape: Shape = RoundedCornerShape(GlassTokens.Radius), floating: Boolean = false): Modifier {
    val base = glassColor()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    // 半透明内容不能覆盖普通 elevation 阴影的内部填充，否则整张卡片会透出灰底。
    // 浮动组件仅在轮廓外绘制阴影，避免灰色阴影填充透过玻璃。
    val frame = if (floating) floatingGlassShadow(shape) else this
    val alpha = if (floating) floatingGlassAlpha() else base.alpha
    val border = if (floating) MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BorderAlpha)
        else Color.White.copy(alpha = if (dark) .12f else .65f)
    return frame.clip(shape)
        .background(Brush.verticalGradient(listOf(base.copy(alpha = alpha), base.copy(alpha = alpha - GlassTokens.HighlightAlpha))))
        .border(BorderStroke(1.dp, border), shape)
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
    val clickable = if (onClick == null) modifier else modifier.clip(shape).clickable(onClick = onClick)
    Column(clickable.glassFrame(shape), content = content)
}

@Composable fun GlassButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(GlassTokens.ControlRadius),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = GlassTokens.SelectedAlpha),
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContainerColor = glassColor(),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.DisabledAlpha),
    ),
    content: @Composable RowScope.() -> Unit,
) {
    val danger = colors.containerColor == MaterialTheme.colorScheme.error
    val resolved = if (danger) colors.copy(
        containerColor = MaterialTheme.colorScheme.error.copy(alpha = GlassTokens.HighlightAlpha),
        contentColor = MaterialTheme.colorScheme.error,
    ) else colors
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = shape, colors = resolved,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = GlassTokens.BorderAlpha)), content = content)
}

@Composable fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick, modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(GlassTokens.ControlRadius),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = glassColor()), content = content)
}

@Composable fun GlassFab(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FloatingActionButton(onClick, modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BorderAlpha), RoundedCornerShape(GlassTokens.ControlRadius)), shape = RoundedCornerShape(GlassTokens.ControlRadius),
        containerColor = glassColor().copy(alpha = floatingGlassAlpha()), contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(GlassTokens.FloatingElevation), content = content)
}

@Composable fun GlassChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, leadingIcon: (@Composable () -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    FilterChip(selected, onClick, label, modifier.heightIn(min = 48.dp), leadingIcon = leadingIcon,
        shape = RoundedCornerShape(GlassTokens.ControlRadius),
        colors = FilterChipDefaults.filterChipColors(containerColor = glassColor(),
            iconColor = accent,
            selectedContainerColor = accent.copy(alpha = .12f),
            selectedLabelColor = accent, selectedLeadingIconColor = accent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = GlassTokens.BorderAlpha)))
}

@Composable fun GlassSearchBar(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Search, null) },
        shape = RoundedCornerShape(GlassTokens.ControlRadius),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = glassColor(), unfocusedContainerColor = glassColor(),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BorderAlpha)))
}

@Composable fun GlassDialog(
    onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null, properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(onDismissRequest, confirmButton,
        modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BorderAlpha), RoundedCornerShape(GlassTokens.FloatingRadius)),
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = RoundedCornerShape(GlassTokens.FloatingRadius), containerColor = glassColor().copy(alpha = GlassTokens.DialogAlpha),
        tonalElevation = 0.dp, properties = properties)
}

/** 弱操作使用同一圆角和按压反馈，危险操作沿用调用方的红色语义。 */
@Composable fun GlassTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(GlassTokens.ControlRadius),
        colors = colors.copy(containerColor = glassColor()), content = content)
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
fun floatingGlassAlpha(): Float = if (MaterialTheme.colorScheme.background.luminance() < .5f)
    GlassTokens.FloatingDarkAlpha else GlassTokens.FloatingLightAlpha

/** 阴影只在容器轮廓外绘制，不给半透明玻璃增加灰色内层底色。 */
private fun Modifier.floatingGlassShadow(shape: Shape): Modifier = this.then(
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
