package com.yangsong.lizhang.flow

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.R

import org.junit.Assert.assertTrue
import org.junit.Test

class CatAssetTransparencyTest {
    @Test fun `猫咪资源必须有真实透明通道并保留不透明主体`() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val names = listOf(R.drawable.home_hero_cat, R.drawable.page_contacts_cat, R.drawable.page_add_cat, R.drawable.page_statistics_cat, R.drawable.page_settings_cat, R.drawable.launcher_cat)
        names.forEach { name ->
            val image = resources.openRawResource(name).use { BitmapFactory.decodeStream(it)!! }
            assertTrue("$name 缺少透明通道", image.hasAlpha())
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val transparent = pixels.count { (it ushr 24) == 0 }
            val opaque = pixels.count { (it ushr 24) >= 250 }
            assertTrue("$name 背景未透明", transparent > pixels.size / 10)
            assertTrue("$name 主体意外透明", opaque > pixels.size / 10)
            listOf(0 to 0, image.width - 1 to 0, 0 to image.height - 1, image.width - 1 to image.height - 1).forEach { (x, y) ->
                // PNG 边缘允许最多 1/255 的量化残留，仍需有大面积 Alpha=0 的背景。
                assertTrue("${resources.getResourceEntryName(name)} 画布边角不透明", image.getPixel(x, y) ushr 24 <= 1)
            }
            image.recycle()
        }
    }
}
