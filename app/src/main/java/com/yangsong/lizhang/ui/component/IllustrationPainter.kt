package com.yangsong.lizhang.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/** 原始透明 PNG 保留质量；页面装饰图解码限制最长边，避免高分辨率素材放大内存占用。 */
@Composable
fun illustrationPainter(@DrawableRes image: Int): Painter = resourceBitmapPainter(image, maxDimension = 1024)
