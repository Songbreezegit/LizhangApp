package com.yangsong.lizhang.ui.component

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class AppearanceGeometryTest {
    @Test fun 内存预算包含极大画面和正常手机() {
        for ((w, h) in listOf(1080 to 2400, 720 to 1280, 8000 to 9000)) {
            val (sw, sh) = snapshotSize(w, h)
            assertTrue(sw.toLong() * sh * 4 <= SNAPSHOT_BUDGET_BYTES)
            assertTrue(sw <= w && sh <= h)
            assertEquals(w.toFloat() / h, sw.toFloat() / sh, .005f)
        }
    }
    @Test fun 半径覆盖偏心圆的四角并包含余量() {
        for ((x, y) in listOf(0f to 0f, 920f to 1630f, 540f to 1200f)) {
            val radius = revealRadius(1080f, 2400f, x, y)
            for ((cx, cy) in listOf(0f to 0f, 1080f to 0f, 0f to 2400f, 1080f to 2400f)) {
                assertTrue(radius >= hypot(cx - x, cy - y) + 2f)
            }
        }
    }
}
