package com.yangsong.lizhang.ui.component

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

/** 仅缓存应用内静态装饰素材；页面切换复用像素，避免再次在首帧解码大 PNG。 */
private object ResourceBitmapCache {
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized
    fun get(resources: Resources, image: Int, maxDimension: Int?): Bitmap {
        val value = TypedValue().also { resources.getValue(image, it, true) }
        // 使用实际选中的资源路径，语言、深浅色或密度限定素材不会误用旧缓存。
        val key = "${value.assetCookie}:${value.string}:$maxDimension"
        bitmaps.get(key)?.let { return it }
        var sample = 1
        if (maxDimension != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, image, bounds)
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }
        return requireNotNull(BitmapFactory.decodeResource(resources, image, options)) {
            "页面装饰资源无法解码"
        }.also { bitmaps.put(key, it) }
    }
}

@Composable
internal fun resourceBitmapPainter(@DrawableRes image: Int, maxDimension: Int? = null): Painter {
    val resources = LocalContext.current.resources
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(resources, image, maxDimension, configuration) {
        BitmapPainter(ResourceBitmapCache.get(resources, image, maxDimension).asImageBitmap())
    }
}
