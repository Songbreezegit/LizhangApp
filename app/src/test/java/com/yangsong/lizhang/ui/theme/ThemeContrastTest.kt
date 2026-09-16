package com.yangsong.lizhang.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `固定浅色容器上的文字和图标满足正文对比度`() {
        val pairs = listOf(
            InkOnIllustration to BlushSurface,
            CoralOnContainer to CoralContainer,
            MintOnContainer to MintContainer,
            ApricotOnContainer to ApricotContainer,
            LavenderOnContainer to LavenderContainer,
        )

        pairs.forEach { (foreground, background) ->
            assertTrue(
                "对比度不足：${contrastRatio(foreground, background)}",
                contrastRatio(foreground, background) >= 4.5f,
            )
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
