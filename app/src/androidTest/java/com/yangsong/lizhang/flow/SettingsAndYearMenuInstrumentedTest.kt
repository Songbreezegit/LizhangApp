package com.yangsong.lizhang.flow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.screen.SettingsContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState
import com.yangsong.lizhang.ui.viewmodel.SettingsUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsAndYearMenuInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 设置页删除大类标题且保留三张卡片与16dp间隔() {
        val dark = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            LiZhangTheme(dark.value) { SettingsContent(SettingsUiState(), {}, {}, {}, {}) }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithText("隐私说明").performScrollTo()
            listOf("数据管理", "显示", "关于").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
            val groups = compose.onAllNodesWithTag("设置功能分组").fetchSemanticsNodes()
            assertEquals("应保留三张独立功能卡片", 3, groups.size)
            groups.sortedBy { it.boundsInRoot.top }.zipWithNext().forEach { (a, b) ->
                assertEquals("分组之间统一16dp", 16 * density, b.boundsInRoot.top - a.boundsInRoot.bottom, 1f)
            }
        }
    }

    @Test fun 设置页八个功能入口与深色开关保持行为() {
        val calls = mutableListOf<String>()
        val mode = mutableStateOf(AppThemeMode.LIGHT)
        compose.setContent { LiZhangTheme {
            SettingsContent(SettingsUiState(themeMode = mode.value),
                onCsvExport = { calls += "CSV" }, onExcelExport = { calls += "Excel" },
                onBackup = { calls += "备份" }, onThemeModeChange = { mode.value = it },
                onThemeOptions = { calls += "主题" }, onFontGuide = { calls += "字体" },
                onAbout = { calls += "关于" }, onPrivacy = { calls += "隐私" })
        } }
        listOf("数据备份与恢复", "导出 Excel", "导出 CSV", "主题设置", "字体说明", "关于礼账", "隐私说明").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("备份", "Excel", "CSV", "主题", "字体", "关于", "隐私"), calls) }
        compose.onNodeWithText("深色模式").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppThemeMode.DARK, mode.value) }
        compose.onNode(isToggleable()).assertIsOn().performClick()
        compose.runOnIdle { assertEquals(AppThemeMode.LIGHT, mode.value) }
    }

    @Test fun 年份选择回调选中状态与再次点击和外部关闭() {
        val state = mutableStateOf(HomeUiState(isLoading = false, year = 2026, availableYears = listOf(2026, 2025)))
        val selected = mutableListOf<Int>()
        compose.setContent { LiZhangTheme {
            HomeContent(state.value, {}, onYearSelected = { selected += it; state.value = state.value.copy(year = it) })
        } }
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("年份选项-2026").assertIsSelected()
        compose.onNodeWithTag("年份选项-2025").performClick()
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(2025), selected) }
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("年份选项-2025").assertIsSelected()
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
        compose.onNodeWithTag("年份入口").performClick()
        compose.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
    }

    @Test fun 返回键优先关闭菜单且页面滚动关闭菜单() {
        var backCalls = 0
        compose.setContent {
            BackHandler { backCalls++ }
            LiZhangTheme { HomeContent(HomeUiState(isLoading = false, availableYears = listOf(2026, 2025)), {}) }
        }
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("年份菜单").assertIsDisplayed()
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(3000) { compose.onAllNodesWithTag("年份菜单").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backCalls) }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.runOnIdle { assertEquals(1, backCalls) }
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("首页列表").performScrollToIndex(2)
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
    }

    @Test fun 多年份菜单限高且独立滚动不关闭() {
        var selected: Int? = null
        compose.setContent { LiZhangTheme {
            HomeContent(HomeUiState(isLoading = false, year = 2026, availableYears = (2026 downTo 1900).toList()),
                {}, onYearSelected = { selected = it })
        } }
        compose.onNodeWithTag("年份入口").performClick()
        compose.onNodeWithTag("年份菜单").performScrollToNode(hasTestTag("年份选项-1900"))
        compose.onNodeWithTag("年份菜单").assertIsDisplayed()
        compose.onNodeWithTag("年份选项-1900").performClick()
        compose.runOnIdle { assertEquals(1900, selected) }
        compose.onNodeWithTag("年份菜单").assertDoesNotExist()
    }

    @Test fun 年份菜单深浅色与大字体锚定8dp且触控高度至少48dp() {
        val dark = mutableStateOf(false)
        val font = mutableFloatStateOf(1f)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                LiZhangTheme(dark.value) {
                    Box(Modifier.width(360.dp)) {
                        HomeContent(HomeUiState(isLoading = false, year = 2026, availableYears = listOf(2026, 2025)), {})
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle { dark.value = night; font.floatValue = scale }
            compose.onNodeWithTag("年份入口").performClick()
            val button = compose.onNodeWithTag("年份入口").fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithTag("年份菜单").fetchSemanticsNode().boundsInRoot
            assertEquals("菜单与入口之间8dp", 8 * density, menu.top - button.bottom, 1f)
            assertEquals("菜单与入口左对齐", button.left, menu.left, 1f)
            assertEquals("菜单宽度168dp", 168 * density, menu.width, 1f)
            assertTrue("菜单不能无限增长", menu.height <= 240 * density + 1)
            for (year in listOf(2026, 2025)) {
                val item = compose.onNodeWithTag("年份选项-$year").fetchSemanticsNode().boundsInRoot
                assertTrue("年份触控高度至少48dp", item.height >= 48 * density - 1)
            }
            compose.onNodeWithTag("年份入口").performClick()
        }
    }
}
