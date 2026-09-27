package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** 仅用于覆盖页面内容的悬浮控件；普通卡片仍使用 glassFrame。 */
object FrostedGlassTokens {
    val BlurRadius = 20.dp
    const val LightTintAlpha = .18f
    const val DarkTintAlpha = .32f
    const val BorderAlpha = .12f
}

/**
 * 采样调用方提供的页面图层，模糊只绘制在当前轮廓内，子组件保持锐利。
 * API 26–30 由 Haze 自动回退；显式设置低透明度 fallbackTint，避免库默认加深色块。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.frostedGlassFrame(
    hazeState: HazeState,
    shape: Shape = RoundedCornerShape(GlassTokens.FloatingRadius),
): Modifier {
    val preview = LocalInspectionMode.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val tint = HazeTint(colors.surface.copy(alpha = if (dark)
        FrostedGlassTokens.DarkTintAlpha else FrostedGlassTokens.LightTintAlpha))
    return floatingGlassShadow(shape)
        .clip(shape)
        .hazeEffect(
            state = hazeState,
            style = HazeStyle(
                // 这是采样图层下方的底色，不是覆盖在采样结果上方的实体 Surface。
                backgroundColor = colors.background,
                tint = tint,
                blurRadius = FrostedGlassTokens.BlurRadius,
                noiseFactor = 0f,
                fallbackTint = tint,
            ),
        ) {
            // Layoutlib 不支持真实 RenderEffect，预览明确采用同一低透明度回退。
            blurEnabled = hazeState.blurEnabled && !preview
            // 固定三分之一分辨率采样减少浮层实时渲染成本；前景不参与降采样。
            inputScale = HazeInputScale.Fixed(.3334f)
        }
        .border(1.dp, colors.onSurface.copy(alpha = FrostedGlassTokens.BorderAlpha), shape)
}
