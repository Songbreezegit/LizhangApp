package com.yangsong.lizhang.flow

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.ContactsContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 使用人工色块采样真实绘制帧，不读取页面内容、通讯录或用户数据。 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NotchedBottomNavInstrumentedTest {
    private class DurationScale : MotionDurationScale {
        var factor by mutableFloatStateOf(1f)
        override val scaleFactor: Float get() = factor
    }

    private val durationScale = DurationScale()
    @get:Rule val compose = createComposeRule(effectContext = durationScale)
    private val destination = mutableStateOf<AppDestination>(AppDestination.Home)
    private val navigations = mutableListOf<AppDestination>()
    private var density = 1f
    private var iconTints = emptyList<Color>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.getExternalFilesDir(null), "navigation-v095").apply { mkdirs() }
    private val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val items = listOf(
        AppDestination.Home to R.string.nav_home,
        AppDestination.Contacts to R.string.nav_contacts,
        AppDestination.AddGift to R.string.nav_add_gift,
        AppDestination.Settings to R.string.nav_settings,
    )
    private lateinit var labels: Map<AppDestination, String>

    private fun start(
        initial: AppDestination = AppDestination.Home,
        locale: String = "zh-CN",
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        dark: Boolean = false,
    ) {
        destination.value = initial
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(locale))
        })
        labels = items.associate { (item, id) -> item to localized.getString(id) }
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                LiZhangTheme(darkTheme = dark) {
                    iconTints = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant)
                    val haze = rememberHazeState(blurEnabled = false)
                    Box(Modifier.width(320.dp).height(240.dp).testTag("导航动效采样画布")) {
                        Box(Modifier.fillMaxSize().background(SampleBackground).hazeSource(haze))
                        BottomNavBar(destination.value, {
                            navigations += it
                            destination.value = it
                        }, Modifier.align(Alignment.BottomCenter), haze)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tab(item: AppDestination) = compose.onNode(hasText(labels.getValue(item)) and tabRole)
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun bubble() = bounds("底部导航浮钮")
    private fun item(item: AppDestination) = bounds("底部导航项${item.route}")
    private fun settle() { compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }

    @Test fun 首次显示直接位于当前入口并保留四个可访问点击区() {
        start(initial = AppDestination.Settings)
        assertEquals("首次显示没有从首页滑过来", item(AppDestination.Settings).center.x, bubble().center.x, density)
        compose.onAllNodes(tabRole).assertCountEquals(4)
        val body = bounds("底部导航主体")
        items.forEach { (route, _) ->
            tab(route).assertIsDisplayed()
            val clickArea = tab(route).fetchSemanticsNode().boundsInRoot
            assertTrue("点击区至少48dp", clickArea.width >= 48 * density - 1 && clickArea.height >= 48 * density - 1)
            val label = bounds("底部导航标签${route.route}")
            assertTrue("标签保留在主体内", label.bottom <= body.bottom + 1)
        }
        tab(AppDestination.Settings).assertIsSelected().performClick()
        compose.runOnIdle { assertTrue("重复点击当前入口不再次导航", navigations.isEmpty()) }
        assertNotch(frame("初始我的"))
    }

    @Test fun 水平切换的实际中间帧浮钮和透明凹槽同步移动且图标先落后起() {
        start()
        compose.mainClock.autoAdvance = false
        val initial = frame("切换起点")
        val homeIconStart = paintedIconCenterY(initial, AppDestination.Home)
        val settingsIconStart = paintedIconCenterY(initial, AppDestination.Settings)
        tab(AppDestination.Settings).performClick()
        compose.mainClock.advanceTimeByFrame()
        val samples = (1..5).map { index ->
            compose.mainClock.advanceTimeBy(64)
            frame("切换中间$index").also(::assertNotch)
        }
        settle()
        val end = frame("切换终点")
        assertNotch(initial)
        assertNotch(end)
        val interior = samples.filter { it.bubble.center.x > initial.bubble.center.x + density && it.bubble.center.x < end.bubble.center.x - density }
        assertTrue("必须采到多个真实水平中间帧", interior.size >= 2)
        (listOf(initial) + samples + end).zipWithNext().forEach { (before, after) ->
            assertTrue("切换时浮钮不倒退", after.bubble.center.x >= before.bubble.center.x - 1f)
        }
        val homeIconEnd = paintedIconCenterY(end, AppDestination.Home)
        val settingsIconEnd = paintedIconCenterY(end, AppDestination.Settings)
        File(directory, "图标真实绘制升降.txt").writeText(
            "首页起点=$homeIconStart\n首页终点=$homeIconEnd\n我的起点=$settingsIconStart\n我的终点=$settingsIconEnd\n")
        assertTrue("实际绘制的原图标落回底栏", homeIconEnd > homeIconStart + 8 * density)
        assertTrue("实际绘制的新图标上浮", settingsIconEnd < settingsIconStart - 8 * density)
        assertEquals(item(AppDestination.Settings).center.x, end.bubble.center.x, density)
        compose.runOnIdle { assertEquals(listOf(AppDestination.Settings), navigations) }
    }

    @Test fun 快速改选从当前帧继续且重复点击不增加回调() {
        start()
        compose.mainClock.autoAdvance = false
        // 先测同一加速段下一帧的自然位移；改选不是静止动画，不能用固定小位移阈值。
        tab(AppDestination.Settings).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(144)
        val controlBefore = frame("改选对照之前")
        val controlBeforeTime = compose.mainClock.currentTime
        // Android performClick 是 down→move→up 的真实触摸，会推进一帧事件时间。
        // 对照也点击已选中的入口，消耗同样的事件时间，但不改变导航目标。
        tab(AppDestination.Settings).performClick()
        val controlClickTime = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        val controlAfter = frame("改选对照自然下一帧")
        val controlAfterTime = compose.mainClock.currentTime
        val naturalStep = abs(controlAfter.bubble.center.x - controlBefore.bubble.center.x)
        tab(AppDestination.Home).performClick()
        settle()
        compose.runOnIdle { navigations.clear() }

        tab(AppDestination.Settings).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(144)
        val before = frame("连续改选之前")
        val beforeTime = compose.mainClock.currentTime
        tab(AppDestination.Contacts).performClick()
        val clickTime = compose.mainClock.currentTime
        compose.mainClock.advanceTimeByFrame()
        val first = frame("连续改选首帧")
        val firstTime = compose.mainClock.currentTime
        val retargetStep = abs(first.bubble.center.x - before.bubble.center.x)
        File(directory, "连续改选首帧位移.txt").writeText(
            "自然下一帧=$naturalStep\n改选下一帧=$retargetStep\n" +
                "对照点击前=$controlBeforeTime\n对照点击后=$controlClickTime\n对照帧后=$controlAfterTime\n" +
                "改选点击前=$beforeTime\n改选点击后=$clickTime\n改选帧后=$firstTime\n")
        assertEquals("对照与改选使用相同的真实点击时长", controlAfterTime - controlBeforeTime, firstTime - beforeTime)
        assertTrue("改选首帧位移不超过自然下一帧，改选=$retargetStep 自然=$naturalStep", retargetStep <= naturalStep + density)
        assertTrue("改选不能直接跳到新目标", abs(first.bubble.center.x - item(AppDestination.Contacts).center.x) > density)
        assertTrue("改选不能直接跳到旧目标", abs(first.bubble.center.x - item(AppDestination.Settings).center.x) > density)
        tab(AppDestination.Contacts).performClick()
        compose.mainClock.advanceTimeBy(96)
        assertNotch(frame("连续改选中间"))
        settle()
        assertEquals(item(AppDestination.Contacts).center.x, bubble().center.x, density)
        tab(AppDestination.Contacts).assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(AppDestination.Settings, AppDestination.Contacts), navigations) }
    }

    @Test fun 从右向左布局的端项和缺口保持对应且点击导航不反转() {
        start(direction = LayoutDirection.Rtl)
        assertTrue("RTL首页位于右端", item(AppDestination.Home).center.x > item(AppDestination.Settings).center.x)
        assertEquals(item(AppDestination.Home).center.x, bubble().center.x, density)
        assertNotch(frame("RTL首页"))
        tab(AppDestination.Settings).performClick()
        settle()
        assertEquals(item(AppDestination.Settings).center.x, bubble().center.x, density)
        assertNotch(frame("RTL我的"))
        compose.runOnIdle { assertEquals(AppDestination.Settings, destination.value) }
    }

    @Test fun 禁用动画后新选中入口和真实凹槽立即到位() {
        durationScale.factor = 0f
        start()
        compose.mainClock.autoAdvance = false
        tab(AppDestination.Settings).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("零动效缩放直接显示目标", item(AppDestination.Settings).center.x, bubble().center.x, density)
        assertNotch(frame("禁用动画"))
        tab(AppDestination.Settings).assertIsSelected()
    }

    @Test fun 西语320dp大字体四项文字完整且滑出取消点击() {
        verifyLargeLabels("es")
    }

    @Test fun 法语320dp大字体四项文字完整且深色凹槽保留() {
        verifyLargeLabels("fr", dark = true)
    }

    @Test fun 联系人实际页面在西法语窄屏大字体下按钮和末项避开导航栏() {
        val locale = mutableStateOf("es")
        val contacts = (1L..32L).map {
            ContactLedgerSummary(Contact(it, "示例联系人$it", relationship = "朋友"), 0, 0)
        }
        var clicked = 0L
        compose.setContent {
            val localized = remember(locale.value) {
                context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(locale.value))
                })
            }
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density, 1.5f),
            ) {
                LiZhangTheme {
                    val haze = rememberHazeState(blurEnabled = false)
                    Box(Modifier.width(320.dp).fillMaxHeight().testTag("导航动效采样画布")) {
                        Box(Modifier.fillMaxSize().hazeSource(haze)) {
                            ContactsContent(ContactsUiState(isLoading = false, contacts = contacts), {}, {}, { clicked = it }, {})
                        }
                        BottomNavBar(AppDestination.Contacts, {}, Modifier.align(Alignment.BottomCenter), haze)
                    }
                }
            }
        }
        for (tag in listOf("es", "fr")) {
            compose.runOnIdle { locale.value = tag }
            compose.onNodeWithTag("联系人列表").performScrollToIndex(contacts.size + 1)
            val last = compose.onNode(hasText(contacts.last().contact.name) and hasClickAction()).assertIsDisplayed()
            val lastBounds = last.fetchSemanticsNode().boundsInRoot
            val listBounds = bounds("联系人列表")
            val fab = bounds("联系人添加菜单")
            val body = bounds("底部导航主体")
            val circle = bubble()
            frame("联系人实际页面-$tag")
            File(directory, "联系人实际布局-$tag.txt").writeText(
                "FAB=$fab\n导航主体=$body\n导航浮钮=$circle\n最后一项=$lastBounds\n列表=$listBounds\n")
            assertFalse("$tag 大字体联系人添加按钮与导航主体不能重叠：FAB=$fab 主体=$body", fab.overlaps(body))
            assertFalse("$tag 大字体联系人添加按钮与导航浮钮不能重叠：FAB=$fab 浮钮=$circle", fab.overlaps(circle))
            assertTrue("最后一项完整处于列表可见范围", lastBounds.top >= listBounds.top - 1 && lastBounds.bottom <= listBounds.bottom + 1)
            assertFalse("最后一项不能被联系人按钮遮挡", lastBounds.overlaps(fab))
            assertFalse("最后一项不能被导航主体遮挡", lastBounds.overlaps(body))
            assertFalse("最后一项不能被导航浮钮遮挡", lastBounds.overlaps(circle))
            last.performClick()
            compose.runOnIdle { assertEquals("完整末项仍可点击", contacts.last().contact.id, clicked) }
        }
    }

    private fun verifyLargeLabels(locale: String, dark: Boolean = false) {
        start(locale = locale, fontScale = 1.5f, dark = dark)
        items.forEach { (route, _) ->
            val result = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("底部导航标签${route.route}", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            assertTrue("${labels.getValue(route)}不截断", result.isNotEmpty() && result.none { it.hasVisualOverflow })
            val label = bounds("底部导航标签${route.route}")
            val column = item(route)
            assertTrue("完整标签位于对应入口内", label.left >= column.left - 1 && label.right <= column.right + 1)
            tab(route).assertIsDisplayed().performClick()
            settle()
            tab(route).assertIsSelected()
            assertNotch(frame("大字体-$locale-${route.route}"))
        }
        val count = navigations.size
        tab(AppDestination.Contacts).performTouchInput {
            down(center)
            moveTo(Offset(center.x, -80 * density), delayMillis = 100)
            up()
        }
        compose.runOnIdle { assertEquals("手指滑出点击区后取消导航", count, navigations.size) }
    }

    private data class Frame(val image: ImageBitmap, val pixels: PixelMap, val canvas: Rect, val body: Rect, val bubble: Rect)

    private fun frame(name: String): Frame {
        compose.waitForIdle()
        val canvas = bounds("导航动效采样画布")
        val body = bounds("底部导航主体")
        val circle = bubble()
        val image = compose.onNodeWithTag("导航动效采样画布").captureToImage()
        File(directory, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(directory, "$name.txt").writeText("浮钮=$circle\n主体=$body\n")
        return Frame(image, image.toPixelMap(), canvas, body, circle)
    }

    /** 浮钮下沿以下取样：凹槽仍透出原背景，两侧主体已经有玻璃色。 */
    private fun assertNotch(frame: Frame) {
        val y = frame.bubble.bottom + 4 * density
        assertTrue("浮钮下方存在可见凹槽间隙", y < frame.body.bottom)
        // 固定槽位的图标会穿过移动凹槽；多点取样避开前景线条，但仍要求可见透明区域。
        val gapDistances = listOf(2f, 4f, 6f).flatMap { below ->
            listOf(-6f, 0f, 6f).map { offset ->
                distance(patch(frame, frame.bubble.center.x + offset * density, frame.bubble.bottom + below * density), SampleBackground)
            }
        }.sorted()
        val sideX = if (frame.bubble.right + 14 * density < frame.body.right - 5 * density)
            frame.bubble.right + 14 * density else frame.bubble.left - 14 * density
        val glass = patch(frame, sideX, y)
        val gapDistance = gapDistances.take(3).average().toFloat()
        val glassDistance = distance(glass, SampleBackground)
        assertTrue("真实绘制凹槽应比主体更透出背景，间隙=$gapDistance 主体=$glassDistance 浮钮=${frame.bubble}",
            glassDistance > gapDistance + .035f)
        assertTrue("至少三处间隙采样透出背景，不能只靠单个抗锯齿像素", gapDistances.count { it < glassDistance - .035f } >= 3)
    }

    private fun patch(frame: Frame, rootX: Float, rootY: Float): Color {
        val cx = (rootX - frame.canvas.left).roundToInt()
        val cy = (rootY - frame.canvas.top).roundToInt()
        val radius = (.5f * density).roundToInt().coerceAtLeast(1)
        var red = 0f; var green = 0f; var blue = 0f; var count = 0
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            val color = frame.pixels[x.coerceIn(0, frame.pixels.width - 1), y.coerceIn(0, frame.pixels.height - 1)]
            red += color.red; green += color.green; blue += color.blue; count++
        }
        return Color(red / count, green / count, blue / count)
    }

    /** 语义区域会被图标槽位裁切；用真实像素中前景颜色的质心检查完整图标升降。 */
    private fun paintedIconCenterY(frame: Frame, route: AppDestination): Float {
        val centerX = item(route).center.x - frame.canvas.left
        val left = (centerX - 18 * density).roundToInt().coerceAtLeast(0)
        val right = (centerX + 18 * density).roundToInt().coerceAtMost(frame.pixels.width - 1)
        val top = (frame.body.top - frame.canvas.top - 18 * density).roundToInt().coerceAtLeast(0)
        val bottom = (frame.body.top - frame.canvas.top + 32 * density).roundToInt().coerceAtMost(frame.pixels.height - 1)
        var totalY = 0f
        var count = 0
        for (y in top..bottom) for (x in left..right) {
            if (iconTints.any { distance(frame.pixels[x, y], it) < .09f }) {
                totalY += y
                count++
            }
        }
        assertTrue("真实图标必须有可识别的完整前景：${route.route}", count >= 12)
        return totalY / count + frame.canvas.top
    }

    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private companion object { val SampleBackground = Color(0xFF087E50) }
}
