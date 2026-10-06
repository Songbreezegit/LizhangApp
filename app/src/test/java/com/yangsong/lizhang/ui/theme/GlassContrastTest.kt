package com.yangsong.lizhang.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.yangsong.lizhang.ui.component.GlassTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    @Test fun `渐变各位置及玻璃层上的文字金额保持可读`() {
        fun verify(foregrounds: List<Color>, stops: List<Color>, surface: Color, alpha: Float) {
            stops.forEach { stop ->
                listOf(stop, surface.copy(alpha = alpha).compositeOver(stop),
                    surface.copy(alpha = alpha - GlassTokens.HighlightAlpha).compositeOver(stop)).forEach { background ->
                    foregrounds.forEach { foreground ->
                        val ratio = (maxOf(foreground.luminance(), background.luminance()) + .05f) /
                            (minOf(foreground.luminance(), background.luminance()) + .05f)
                        assertTrue("渐变与玻璃表面文字对比度不足：$ratio", ratio >= 4.5f)
                    }
                }
            }
        }
        verify(listOf(InkPrimary, InkSecondary, CoralPrimary, MintPrimary, AppError),
            listOf(GradientTop, CreamBackground, GradientBottom), CardWhite, GlassTokens.LightAlpha)
        verify(listOf(DarkText, DarkSecondary, DarkCoral, DarkBlue, Color(0xFFFF8A94)),
            listOf(DarkGradientTop, DarkBackground, DarkGradientBottom), DarkSurface, GlassTokens.DarkAlpha)
    }
}
