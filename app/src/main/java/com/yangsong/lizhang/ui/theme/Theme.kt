package com.yangsong.lizhang.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SealRed, onPrimary = PaperSurface, primaryContainer = SealRedContainer,
    secondary = QingGray, tertiary = TeaBrown, background = PaperBackground,
    surface = PaperSurface, onBackground = InkPrimary, onSurface = InkPrimary,
    onSurfaceVariant = InkSecondary, outline = PaperDivider, error = AppError,
)
private val DarkColors = darkColorScheme(
    primary = DarkSealRed, onPrimary = DarkInkPrimary, primaryContainer = DarkSealRedContainer,
    secondary = DarkQingGray, tertiary = DarkTeaBrown, background = DarkPaperBackground,
    surface = DarkPaperSurface, onBackground = DarkInkPrimary, onSurface = DarkInkPrimary,
    onSurfaceVariant = DarkInkSecondary, outline = DarkPaperDivider, error = DarkAppError,
)

@Composable
fun LiZhangTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = LiZhangTypography, content = content)
}
