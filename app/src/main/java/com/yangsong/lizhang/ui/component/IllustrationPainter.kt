package com.yangsong.lizhang.ui.component

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

/** 原始透明 PNG 保留质量；页面装饰图解码限制最长边，避免高分辨率素材放大内存占用。 */
@Composable
fun illustrationPainter(@DrawableRes image: Int): Painter {
    val resources = LocalContext.current.resources
    return remember(resources, image) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, image, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
        val bitmap = BitmapFactory.decodeResource(resources, image, options)
        BitmapPainter(requireNotNull(bitmap) { "猫咪插画资源无法解码" }.asImageBitmap())
    }
}
