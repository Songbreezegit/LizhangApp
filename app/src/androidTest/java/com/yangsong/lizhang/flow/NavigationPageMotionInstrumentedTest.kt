package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** 直接运行正式导航图，采样业务页面正文；底栏移动不能充当页面动效的证据。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationPageMotionInstrumentedTest {
    @get:org.junit.Rule(order = 0) val acceptedPrivacy = com.yangsong.lizhang.fixtures.AcceptedPrivacyRule()
    private val durationScale = object : MotionDurationScale { override val scaleFactor = 1f }
    @get:Rule(order = 1) val compose = createComposeRule(effectContext = durationScale)
    private val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
    private val directory get() = File(app.getExternalFilesDir(null), "navigation-pages-v096").apply { mkdirs() }
    private val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private var density = 1f

    private fun start() {
        compose.setChineseContent {
            density = LocalDensity.current.density
            LiZhangTheme {
                Box(Modifier.fillMaxSize().testTag("真实导航采样")) {
                    LiZhangNavGraph(app.appContainer, guideEnabled = false)
                }
            }
        }
        compose.waitUntil(8000) {
            runCatching { compose.onNodeWithTag("首页列表").assertIsDisplayed(); true }.getOrDefault(false)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and tabRole)
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun settle() { compose.mainClock.advanceTimeBy(900); compose.waitForIdle() }

    @Test fun 首页联系人我的往返具有多个真实正文中间帧且最终目的地正确() {
        start()
        for ((label, tag) in listOf("联系人" to "联系人列表", "我的" to "设置列表", "首页" to "首页列表")) {
            verifyPageMotion("切换到$label", tag, { tab(label).performClick() })
            tab(label).assertIsSelected()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            listOf("首页列表", "联系人列表", "设置列表").filter { it != tag }.forEach {
                compose.onNodeWithTag(it).assertDoesNotExist()
            }
        }
    }

    @Test fun 进入记一笔时底栏逐帧退场并在返回首页时逐帧恢复() {
        start()
        val initialBar = bounds("底部导航主体")
        val leavingPositions = mutableListOf<Rect>()
        verifyPageMotion("进入记一笔", "礼金保存栏", { tab("记一笔").performClick() }) { index ->
            if (index < 2) {
                leavingPositions += bounds("底部导航主体")
                capture("底栏退场${index + 1}", wholeWindow = true)
            }
            if (index == 0) {
                tab("首页").assertIsNotEnabled()
                tab("联系人").assertIsNotEnabled().performTouchInput { click() }
            }
        }
        assertEquals("退场保留两个真实中间位置", 2, leavingPositions.size)
        assertTrue("底栏先轻落再离开", leavingPositions.first().top > initialBar.top + density)
        assertTrue("退场位置连续向下", leavingPositions.last().top > leavingPositions.first().top + density)
        compose.onNodeWithTag("底部导航主体").assertDoesNotExist()
        compose.onNodeWithTag("礼金保存栏").assertIsDisplayed()
        compose.onNodeWithTag("首页列表").assertDoesNotExist()

        val enteringPositions = mutableListOf<Rect>()
        verifyPageMotion("记一笔返回", "首页列表", {
            compose.onNodeWithContentDescription("返回").performClick()
        }) { index ->
            if (index < 2) {
                enteringPositions += bounds("底部导航主体")
                capture("底栏入场${index + 1}", wholeWindow = true)
            }
        }
        val restoredBar = bounds("底部导航主体")
        assertEquals("入场保留两个真实中间位置", 2, enteringPositions.size)
        assertTrue("底栏从下方进入", enteringPositions.first().top > restoredBar.top + density)
        assertTrue("入场位置连续向上", enteringPositions.last().top < enteringPositions.first().top - density)
        assertEquals("返回后底栏恢复原位置", initialBar.top, restoredBar.top, density)
        tab("首页").assertIsSelected()
        compose.onNodeWithTag("礼金保存栏").assertDoesNotExist()
    }

    private fun verifyPageMotion(
        name: String,
        destinationTag: String,
        navigate: () -> Unit,
        onIntermediate: (Int) -> Unit = {},
    ) {
        val before = capture("$name-起点")
        navigate()
        compose.mainClock.advanceTimeByFrame()
        val positions = mutableListOf<Rect>()
        val intermediate = (0..4).map { index ->
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            positions += bounds(destinationTag)
            capture("$name-中间${index + 1}").also { onIntermediate(index) }
        }
        settle()
        compose.onNodeWithTag(destinationTag).assertIsDisplayed()
        val finalBounds = bounds(destinationTag)
        val after = capture("$name-终点")
        val movingFrames = intermediate.filter { difference(it, before) > .005 && difference(it, after) > .005 }
        assertTrue("$name 至少两个正文中间帧同时区别于起点与终点", movingFrames.size >= 2)
        assertTrue("$name 的正文中间帧必须彼此不同", intermediate.zipWithNext().count {
            difference(it.first, it.second) > .002
        } >= 2)
        assertTrue("$name 真实页面布局需移动，不能只让导航圆钮运动", positions.count {
            abs(it.center.x - finalBounds.center.x) > density
        } >= 2)
        (listOf(before) + intermediate + after).forEachIndexed { index, bitmap ->
            assertTrue("$name 第 $index 帧正文不能空白", hasContent(bitmap))
        }
    }

    private fun capture(name: String, wholeWindow: Boolean = false): ImageBitmap {
        val image = compose.onNodeWithTag(if (wholeWindow) "真实导航采样" else "业务导航页面").captureToImage()
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    /** 正文范围剔除系统栏和悬浮导航，细文字的局部反差用于排除纯色或渐变空屏。 */
    private fun hasContent(image: ImageBitmap): Boolean {
        val pixels = image.toPixelMap()
        val stride = max(2, image.width / 240)
        var edges = 0
        for (y in image.height * 15 / 100 until image.height * 75 / 100 step stride) {
            for (x in image.width * 6 / 100 until image.width * 94 / 100 step stride) {
                val a = pixels[x, y]
                val b = pixels[x + stride, y]
                val c = pixels[x, y + stride]
                val contrast = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue),
                    abs(a.red - c.red), abs(a.green - c.green), abs(a.blue - c.blue))
                if (contrast >= 24f / 255 && ++edges >= 40) return true
            }
        }
        return false
    }

    private fun difference(before: ImageBitmap, after: ImageBitmap): Double {
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        val stride = max(2, before.width / 240)
        var changed = 0
        var total = 0
        for (y in before.height * 15 / 100 until before.height * 75 / 100 step stride) {
            for (x in before.width * 6 / 100 until before.width * 94 / 100 step stride) {
                val p = a[x, y]
                val q = b[x, y]
                if (maxOf(abs(p.red - q.red), abs(p.green - q.green), abs(p.blue - q.blue)) > .06f) changed++
                total++
            }
        }
        return changed.toDouble() / total
    }
}
