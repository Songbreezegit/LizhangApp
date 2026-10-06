package com.yangsong.lizhang.flow

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.*
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** 只使用程序生成的图案和虚构联系人，不采集用户数据。 */
class FrostedGlassInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.getExternalFilesDir(null), "frosted-v080").apply { mkdirs() }

    @Test fun 模糊削弱背景高频边缘且前景保持锐利并随背景刷新() {
        val dark = mutableStateOf(false)
        val blue = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            LiZhangTheme(dark.value) {
                val haze = rememberHazeState()
                Box(Modifier.size(320.dp, 300.dp).testTag("采样画布")) {
                    Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                        val stripe = 4.dp.toPx()
                        for (i in 0..(size.width / stripe).toInt()) {
                            drawRect(if (i % 2 == 0) Color.Black else if (blue.value) Color.Blue else Color.Red,
                                Offset(i * stripe, 0f), Size(stripe, size.height))
                        }
                    }
                    Box(Modifier.offset(24.dp, 80.dp).size(272.dp, 160.dp).frostedGlassFrame(haze)) {
                        // 高对比前景同样的细条纹，必须保持原始锐度。
                        Canvas(Modifier.offset(40.dp, 80.dp).size(160.dp, 16.dp)) {
                            val stripe = 4.dp.toPx()
                            for (i in 0..(size.width / stripe).toInt()) {
                                drawRect(if (i % 2 == 0) Color.Black else Color.White,
                                    Offset(i * stripe, 0f), Size(stripe, size.height))
                            }
                        }
                        Text("清晰前景", Modifier.padding(40.dp, 110.dp))
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("采样画布").captureToImage().toPixelMap()
        fun x(dp: Int) = (dp * density).toInt()
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; blue.value = false }
            val before = pixels()
            val raw = variation(before, x(45), x(270), x(40))
            val blurred = variation(before, x(45), x(270), x(120))
            val foreground = variation(before, x(75), x(210), x(168))
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue("背景高频边缘应衰减至少80%：$blurred / $raw", blurred < raw * .20f)
            } else {
                assertTrue("旧系统保持低透明度回退", blurred > raw * .50f)
            }
            assertTrue("前景边缘必须锐利：$foreground / $raw", foreground > raw * 2f)
            val red = before[x(150), x(120)]
            compose.runOnIdle { blue.value = true }
            val after = pixels()
            val bluePixel = after[x(150), x(120)]
            assertTrue("采样结果随背景更新", bluePixel.blue - red.blue > .15f)
            assertTrue("更新后不残留旧背景", red.red - bluePixel.red > .15f)
            capture("频率-$night")
            File(directory, "频率-$night.txt").writeText(
                "原始边缘=$raw\n玻璃边缘=$blurred\n前景边缘=$foreground\n")
        }
    }

    @Test fun 禁用模糊时仍保持低透明度回退() {
        val background = mutableStateOf(Color.Red)
        compose.setContent { LiZhangTheme {
            val haze = rememberHazeState(blurEnabled = false)
            Box(Modifier.size(200.dp).testTag("回退画布")) {
                Canvas(Modifier.fillMaxSize().hazeSource(haze)) { drawRect(background.value) }
                Box(Modifier.fillMaxSize().frostedGlassFrame(haze))
            }
        } }
        fun center(): Color {
            val p = compose.onNodeWithTag("回退画布").captureToImage().toPixelMap()
            return p[p.width / 2, p.height / 2]
        }
        val red = center()
        compose.runOnIdle { background.value = Color.Blue }
        val blue = center()
        assertTrue("回退不能恢复厚重白色卡片", red.red - blue.red > .70f)
        assertTrue("回退仍透出页面色彩", blue.blue - red.blue > .70f)
    }

    @Test fun 四类浮层图案背景深浅色与大字体截图() {
        val dark = mutableStateOf(false)
        val font = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                LiZhangTheme(dark.value) {
                    val haze = rememberHazeState()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().hazeSource(haze)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stripe = 6.dp.toPx()
                                for (i in 0..(size.width / stripe).toInt()) {
                                    drawRect(if (i % 2 == 0) Color(0xFF9CB5CF) else Color(0xFFE4B48E),
                                        Offset(i * stripe, 0f), Size(stripe, size.height))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(32) { Text("示例背景 $it · 备注 / ¥100.00") }
                            }
                        }
                        GiftSaveBar(false, {}, Modifier.align(Alignment.TopCenter).padding(top = 50.dp), hazeState = haze)
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                            GlassActionMenu(true, {}, {}, listOf(
                                GlassAction("通讯录导入", Icons.Outlined.Contacts) {},
                                GlassAction("手动添加", Icons.Outlined.PersonAdd) {},
                            ), haze)
                        }
                        BottomNavBar(AppDestination.Home, {}, Modifier.align(Alignment.BottomCenter), haze)
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { dark.value = night; font.floatValue = scale }
            capture("四类浮层-$night-$scale")
            compose.onNodeWithText("保存记录").assertIsDisplayed()
            compose.onNodeWithText("通讯录导入").assertIsDisplayed()
        }
    }

    @Test fun 首页搜索通知按钮间距10dp且点击范围不缩小() {
        val dark = mutableStateOf(false)
        val font = mutableFloatStateOf(1f)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                LiZhangTheme(dark.value) {
                    Box(Modifier.width(360.dp)) { HomeContent(HomeUiState(isLoading = false), {}) }
                }
            }
        }
        for (night in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { dark.value = night; font.floatValue = scale }
            val search = compose.onNodeWithContentDescription(context.getString(R.string.action_search)).fetchSemanticsNode().boundsInRoot
            val notice = compose.onNodeWithContentDescription(context.getString(R.string.nav_notifications)).fetchSemanticsNode().boundsInRoot
            val gap = notice.left - search.right
            assertTrue("点击区域至少48dp", search.width >= 48 * density - 1 && notice.width >= 48 * density - 1)
            assertTrue("按钮不得贴合", gap >= 8 * density)
            assertEquals("目标10dp", 10 * density, gap, 1f)
            capture("首页按钮-$night-$scale")
        }
    }

    @Test fun 实际页面滚动与联系人连续十秒滚动浮层位置稳定() {
        val dark = mutableStateOf(false)
        val page = mutableIntStateOf(0)
        val contacts = (1L..200L).map { ContactLedgerSummary(Contact(it, "示例联系人$it", relationship = "朋友"), 20000, 10000) }
        val records = (1L..4L).map { GiftRecordWithContact(
            GiftRecord(it, it, 10000, EventType.OTHER, 1789862400000L, GiftDirection.RECEIVED, customEventName = "升学宴"), "示例联系人$it") }
        compose.setContent { LiZhangTheme(dark.value) {
            val haze = rememberHazeState()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    key(page.intValue, dark.value) {
                        when (page.intValue) {
                            0 -> HomeContent(HomeUiState(isLoading = false, recentRecords = records), {})
                            1 -> ContactsContent(ContactsUiState(isLoading = false, contacts = contacts), {}, {}, {}, {})
                            2 -> AddGiftContent(GiftEditorUiState(contacts = listOf(contacts[0].contact), contactId = 1,
                                amount = "100", eventDate = 1789862400000L, notes = "用于核对模糊的虚构备注\n第二行备注\n第三行备注"),
                                {}, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
                if (page.intValue != 2) BottomNavBar(
                    if (page.intValue == 0) AppDestination.Home else AppDestination.Contacts, {},
                    Modifier.align(Alignment.BottomCenter).testTag("稳定导航"), haze)
            }
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; page.intValue = 0 }
            compose.onNodeWithText("示例联系人4").performScrollTo()
            capture("首页导航-$night")
            compose.runOnIdle { page.intValue = 1 }
            compose.onNodeWithTag("联系人列表").performScrollToIndex(5)
            val fab = compose.onNodeWithTag("联系人添加菜单").fetchSemanticsNode().boundsInRoot
            val nav = compose.onNodeWithTag("稳定导航").fetchSemanticsNode().boundsInRoot
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            capture("联系人滚动前-$night")
            device.executeShellCommand("dumpsys gfxinfo com.yangsong.lizhang reset")
            val start = SystemClock.elapsedRealtime()
            var count = 0
            // 使用真实墙钟触摸输入；计时内不截图、不调用 Compose 空闲同步，以免污染帧统计。
            val x = device.displayWidth / 2
            val top = device.displayHeight / 3
            val bottom = device.displayHeight * 2 / 3
            while (SystemClock.elapsedRealtime() - start < 10_000) {
                if (count++ % 2 == 0) device.swipe(x, bottom, x, top, 12)
                else device.swipe(x, top, x, bottom, 12)
            }
            compose.waitForIdle()
            assertEquals("FAB滑动时不重新布局", fab, compose.onNodeWithTag("联系人添加菜单").fetchSemanticsNode().boundsInRoot)
            assertEquals("导航滑动时不重新布局", nav, compose.onNodeWithTag("稳定导航").fetchSemanticsNode().boundsInRoot)
            File(directory, "帧统计-$night.txt").writeText(device.executeShellCommand("dumpsys gfxinfo com.yangsong.lizhang"))
            capture("联系人按钮-$night")
            compose.onNodeWithTag("联系人添加菜单").performClick()
            capture("联系人菜单-$night")
            val elapsed = SystemClock.elapsedRealtime() - start
            File(directory, "滚动-$night.txt").writeText("时长=$elapsed ms\n手势次数=$count\n浮层位置断言通过\n")
            compose.runOnIdle { page.intValue = 2 }
            compose.onNodeWithTag("记账底部留白").performScrollTo()
            compose.onNode(hasScrollAction()).performTouchInput {
                swipe(center, Offset(center.x, center.y + 350f), durationMillis = 700)
            }
            capture("保存栏-$night")
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun variation(p: PixelMap, left: Int, right: Int, y: Int): Float =
        (left until right).sumOf { x ->
            val a = p[x, y]; val b = p[x + 1, y]
            ((abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) / 3).toDouble()
        }.toFloat() / (right - left)
}
