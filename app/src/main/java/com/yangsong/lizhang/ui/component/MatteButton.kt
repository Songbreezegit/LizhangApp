package com.yangsong.lizhang.ui.component

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/** 固定细颗粒与页面纸纹呼应；纹理只生成一次，按压和重绘时保持静止。 */
private val buttonGrainBrush by lazy {
    val edge = 128
    val random = Random(20261010L)
    val pixels = IntArray(edge * edge) {
        val grain = random.nextFloat() * 2f - 1f
        val alpha = (abs(grain) * 8f).roundToInt()
        (alpha shl 24) or if (grain >= 0f) 0x00FFFFFF else 0
    }
    ShaderBrush(ImageShader(
        Bitmap.createBitmap(pixels, edge, edge, Bitmap.Config.ARGB_8888).asImageBitmap(),
        tileModeX = TileMode.Repeated,
        tileModeY = TileMode.Repeated,
    ))
}

/** 底色与磨砂颗粒均在内容下方绘制，文字、图标及加载指示保持清晰。 */
internal fun Modifier.matteButtonFrame(color: Color, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    onDrawBehind {
        drawPath(path, color)
        clipPath(path) { drawRect(buttonGrainBrush) }
    }
}
