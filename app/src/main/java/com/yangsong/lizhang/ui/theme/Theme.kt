package com.yangsong.lizhang.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    background = AppBackground,
    surface = AppSurface,
)

private val DarkColors = darkColorScheme(
    primary = DarkBrandPrimary,
    onPrimary = DarkBrandOnPrimary,
    secondary = DarkBrandSecondary,
    background = DarkAppBackground,
    surface = DarkAppSurface,
)

@Composable
fun LiZhangTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LiZhangTypography, content = content)
}
