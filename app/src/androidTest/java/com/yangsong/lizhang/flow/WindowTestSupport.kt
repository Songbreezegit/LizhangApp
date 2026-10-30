package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File

/** 保留可见裁剪边界，再平移到屏幕坐标；不同窗口的 root/window 原点不能直接比较。 */
internal fun SemanticsNode.clippedBoundsOnScreen(): Rect =
    boundsInRoot.translate(positionOnScreen - positionInRoot)

/** Activity 的根图像不包含独立 Popup；整屏截图用于覆盖所有前景窗口。 */
internal fun saveWholeScreen(file: File) {
    val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) {
        "无法采集包含独立浮层的屏幕"
    }
    try {
        file.outputStream().use {
            assertTrue("截图应完整写入", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally { bitmap.recycle() }
}
