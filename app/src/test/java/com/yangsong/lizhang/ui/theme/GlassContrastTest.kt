package com.yangsong.lizhang.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.yangsong.lizhang.ui.component.GlassTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    @Test fun `深浅玻璃表面上的正文金额和危险操作保持可读`() {
        val lightGlass = CardWhite.copy(alpha = GlassTokens.LightAlpha).compositeOver(CreamBackground)
        val darkGlass = DarkSurface.copy(alpha = GlassTokens.DarkAlpha).compositeOver(DarkBackground)
        val pairs = listOf(
            InkPrimary to lightGlass, InkSecondary to lightGlass,
            CoralPrimary to lightGlass, MintPrimary to lightGlass, AppError to lightGlass,
            DarkText to darkGlass, DarkSecondary to darkGlass, DarkCoral to darkGlass,
            Color(0xFF72D89F) to darkGlass, Color(0xFFFF8A94) to darkGlass,
        )
        pairs.forEach { (foreground, background) ->
            val ratio = (maxOf(foreground.luminance(), background.luminance()) + .05f) /
                (minOf(foreground.luminance(), background.luminance()) + .05f)
            assertTrue("玻璃表面文字对比度不足：$ratio", ratio >= 4.5f)
        }
    }
}
