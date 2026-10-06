package com.yangsong.lizhang.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
/** 普通内容使用中性表面，蓝橘只用于金额、选中状态和主要操作。 */
private val Light = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = CardWhite,
    primaryContainer = CoralContainer,
    onPrimaryContainer = CoralOnContainer,
    inversePrimary = DarkCoral,
    secondary = MintPrimary,
    onSecondary = CardWhite,
    secondaryContainer = MintContainer,
    onSecondaryContainer = MintOnContainer,
    tertiary = ApricotPrimary,
    onTertiary = CardWhite,
    tertiaryContainer = ApricotContainer,
    onTertiaryContainer = ApricotOnContainer,
    background = CreamBackground,
    onBackground = InkPrimary,
    surface = CardWhite,
    onSurface = InkPrimary,
    surfaceVariant = Color(0xFFE5E9E3),
    onSurfaceVariant = InkSecondary,
    surfaceTint = CoralPrimary,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkText,
    error = AppError,
    onError = CardWhite,
    errorContainer = Color(0xFFFFE5E7),
    onErrorContainer = Color(0xFF84262D),
    outline = SoftDivider,
    outlineVariant = Color(0xFFE3E7E1),
    scrim = Color.Black,
    surfaceBright = CardWhite,
    surfaceDim = Color(0xFFE6EAE3),
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Color(0xFFF0F2ED),
    surfaceContainer = Color(0xFFEBEEE7),
    surfaceContainerHigh = Color(0xFFE6EAE2),
    surfaceContainerHighest = Color(0xFFE0E5DC),
)
private val Dark = darkColorScheme(
    primary = DarkCoral,
    onPrimary = Color(0xFF3D2115),
    primaryContainer = DarkCoralContainer,
    onPrimaryContainer = Color(0xFFFFD7C1),
    inversePrimary = CoralPrimary,
    secondary = DarkBlue,
    onSecondary = Color(0xFF143548),
    secondaryContainer = Color(0xFF304657),
    onSecondaryContainer = Color(0xFFD0E9F8),
    tertiary = DarkCoral,
    onTertiary = Color(0xFF3D2115),
    tertiaryContainer = DarkCoralContainer,
    onTertiaryContainer = Color(0xFFFFD7C1),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFF394149),
    onSurfaceVariant = DarkSecondary,
    surfaceTint = DarkCoral,
    inverseSurface = CreamBackground,
    inverseOnSurface = InkPrimary,
    error = Color(0xFFFFA0A7),
    onError = Color(0xFF571B22),
    errorContainer = Color(0xFF5A2A31),
    onErrorContainer = Color(0xFFFFDADD),
    outline = DarkDivider,
    outlineVariant = Color(0xFF3C444D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF414A53),
    surfaceDim = Color(0xFF1B2025),
    surfaceContainerLowest = Color(0xFF171B20),
    surfaceContainerLow = Color(0xFF242A30),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF333A42),
    surfaceContainerHighest = Color(0xFF3D454E),
)
val LocalThemeDarkFraction = staticCompositionLocalOf { 0f }
val LocalEffectiveDarkTheme = staticCompositionLocalOf { false }

/** 页面和独立 Dialog 继承同一进度，圆形切换直接使用目标配色。 */
@Composable
fun LiZhangTheme(darkTheme: Boolean = isSystemInDarkTheme(), animateColors: Boolean = false,
    content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (darkTheme) 1f else 0f) }
    LaunchedEffect(darkTheme, animateColors) {
        val target = if (darkTheme) 1f else 0f
        if (animateColors && progress.value != target) progress.animateTo(target, tween(240, easing = FastOutSlowInEasing))
        else progress.snapTo(target)
    }
    val fraction = if (animateColors) progress.value else if (darkTheme) 1f else 0f
    CompositionLocalProvider(LocalThemeDarkFraction provides fraction, LocalEffectiveDarkTheme provides darkTheme) {
        // 文字与底色交叉时增加短暂轮廓阴影，降低中间灰色帧的阅读损失；端点恢复原样。
        val halo = 4f * fraction * (1f - fraction)
        val shadow = Shadow(Color.Black.copy(alpha = .85f * halo), blurRadius = 2.5f * halo)
        val typography = if (animateColors && halo > 0f) LiZhangTypography.copy(
            displayLarge = LiZhangTypography.displayLarge.copy(shadow = shadow),
            displayMedium = LiZhangTypography.displayMedium.copy(shadow = shadow),
            displaySmall = LiZhangTypography.displaySmall.copy(shadow = shadow),
            headlineLarge = LiZhangTypography.headlineLarge.copy(shadow = shadow),
            headlineMedium = LiZhangTypography.headlineMedium.copy(shadow = shadow),
            headlineSmall = LiZhangTypography.headlineSmall.copy(shadow = shadow),
            titleLarge = LiZhangTypography.titleLarge.copy(shadow = shadow),
            titleMedium = LiZhangTypography.titleMedium.copy(shadow = shadow),
            titleSmall = LiZhangTypography.titleSmall.copy(shadow = shadow),
            bodyLarge = LiZhangTypography.bodyLarge.copy(shadow = shadow),
            bodyMedium = LiZhangTypography.bodyMedium.copy(shadow = shadow),
            bodySmall = LiZhangTypography.bodySmall.copy(shadow = shadow),
            labelLarge = LiZhangTypography.labelLarge.copy(shadow = shadow),
            labelMedium = LiZhangTypography.labelMedium.copy(shadow = shadow),
            labelSmall = LiZhangTypography.labelSmall.copy(shadow = shadow),
        ) else LiZhangTypography
        MaterialTheme(colorScheme = interpolateScheme(fraction), typography = typography, content = content)
    }
}

/** 所有 Material 色槽连续插值，避免默认容器色在中途突跳。 */
internal fun interpolateScheme(f: Float): ColorScheme = Light.copy(
    primary=lerp(Light.primary,Dark.primary,f), onPrimary=lerp(Light.onPrimary,Dark.onPrimary,f),
    primaryContainer=lerp(Light.primaryContainer,Dark.primaryContainer,f), onPrimaryContainer=lerp(Light.onPrimaryContainer,Dark.onPrimaryContainer,f),
    inversePrimary=lerp(Light.inversePrimary,Dark.inversePrimary,f), secondary=lerp(Light.secondary,Dark.secondary,f),
    onSecondary=lerp(Light.onSecondary,Dark.onSecondary,f), secondaryContainer=lerp(Light.secondaryContainer,Dark.secondaryContainer,f),
    onSecondaryContainer=lerp(Light.onSecondaryContainer,Dark.onSecondaryContainer,f), tertiary=lerp(Light.tertiary,Dark.tertiary,f),
    onTertiary=lerp(Light.onTertiary,Dark.onTertiary,f), tertiaryContainer=lerp(Light.tertiaryContainer,Dark.tertiaryContainer,f),
    onTertiaryContainer=lerp(Light.onTertiaryContainer,Dark.onTertiaryContainer,f), background=lerp(Light.background,Dark.background,f),
    onBackground=lerp(Light.onBackground,Dark.onBackground,f), surface=lerp(Light.surface,Dark.surface,f),
    onSurface=lerp(Light.onSurface,Dark.onSurface,f), surfaceVariant=lerp(Light.surfaceVariant,Dark.surfaceVariant,f),
    onSurfaceVariant=lerp(Light.onSurfaceVariant,Dark.onSurfaceVariant,f), surfaceTint=lerp(Light.surfaceTint,Dark.surfaceTint,f),
    inverseSurface=lerp(Light.inverseSurface,Dark.inverseSurface,f), inverseOnSurface=lerp(Light.inverseOnSurface,Dark.inverseOnSurface,f),
    error=lerp(Light.error,Dark.error,f), onError=lerp(Light.onError,Dark.onError,f),
    errorContainer=lerp(Light.errorContainer,Dark.errorContainer,f), onErrorContainer=lerp(Light.onErrorContainer,Dark.onErrorContainer,f),
    outline=lerp(Light.outline,Dark.outline,f), outlineVariant=lerp(Light.outlineVariant,Dark.outlineVariant,f),
    scrim=lerp(Light.scrim,Dark.scrim,f), surfaceBright=lerp(Light.surfaceBright,Dark.surfaceBright,f),
    surfaceDim=lerp(Light.surfaceDim,Dark.surfaceDim,f), surfaceContainer=lerp(Light.surfaceContainer,Dark.surfaceContainer,f),
    surfaceContainerHigh=lerp(Light.surfaceContainerHigh,Dark.surfaceContainerHigh,f),
    surfaceContainerHighest=lerp(Light.surfaceContainerHighest,Dark.surfaceContainerHighest,f),
    surfaceContainerLow=lerp(Light.surfaceContainerLow,Dark.surfaceContainerLow,f),
    surfaceContainerLowest=lerp(Light.surfaceContainerLowest,Dark.surfaceContainerLowest,f),
)
