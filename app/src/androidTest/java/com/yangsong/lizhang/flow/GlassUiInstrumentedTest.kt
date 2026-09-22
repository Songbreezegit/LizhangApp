package com.yangsong.lizhang.flow

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.ui.component.BottomNavBar
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.screen.ContactsContent
import com.yangsong.lizhang.ui.screen.ReminderAdvanceDialog
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(ContactsUiState(isLoading = false))
    private var manual = 0
    private var imported = 0

    private fun start() {
        compose.setContent { LiZhangTheme {
            ContactsContent(state.value, {}, {}, {}, { manual++ },
                onImportContacts = { imported++ },
                onEnterSelection = { state.value = state.value.copy(isSelectionMode = true) })
        } }
    }
    private fun toggle() = compose.onNodeWithTag("联系人添加菜单").performClick()
    private fun closed() {
        compose.onNodeWithText("手动添加").assertDoesNotExist()
        compose.onNodeWithText("通讯录导入").assertDoesNotExist()
    }

    @Test fun 初始收起再次点击收起() {
        start(); closed(); toggle()
        compose.onNodeWithText("手动添加").assertIsDisplayed()
        compose.onNodeWithText("通讯录导入").assertIsDisplayed()
        toggle(); closed()
    }

    @Test fun 外部点击关闭且不触发页面操作() {
        start(); toggle()
        compose.onNodeWithTag("关闭联系人菜单遮罩").performTouchInput { click(Offset(10f, 100f)) }
        closed()
        compose.runOnIdle { assertEquals(0, manual + imported) }
    }

    @Test fun 返回键优先收起菜单() {
        start(); toggle()
        compose.onNodeWithText("手动添加").assertIsDisplayed()
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        closed()
        compose.onNodeWithTag("联系人添加菜单").assertIsDisplayed()
    }

    @Test fun 两个操作分别回调并自动收起() {
        start(); toggle()
        compose.onNodeWithText("手动添加").performClick(); closed()
        compose.runOnIdle { assertEquals(1, manual); assertEquals(0, imported) }
        toggle(); compose.onNodeWithText("通讯录导入").performClick(); closed()
        compose.runOnIdle { assertEquals(1, manual); assertEquals(1, imported) }
    }

    @Test fun 进入管理隐藏菜单退出不恢复展开() {
        start(); toggle()
        compose.runOnIdle { state.value = state.value.copy(isSelectionMode = true) }
        closed(); compose.onNodeWithTag("联系人添加菜单").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isSelectionMode = false) }
        closed(); compose.onNodeWithTag("联系人添加菜单").assertIsDisplayed()
    }

    @Test fun 千条联系人懒加载且最后一条可见可点击() {
        var clicked = 0L
        val contacts = (1L..1000L).map { ContactLedgerSummary(Contact(it, "虚构测试$it"), 0, 0) }
        compose.setContent { LiZhangTheme {
            ContactsContent(ContactsUiState(contacts = contacts, isLoading = false), {}, {}, { clicked = it }, {})
        } }
        compose.onNodeWithText("虚构测试1000").assertDoesNotExist()
        compose.onNodeWithTag("联系人列表").performScrollToIndex(1002)
        compose.onNodeWithText("虚构测试1000").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1000L, clicked) }
    }

    @Test fun 提醒日期四项仍返回原天数() {
        val selected = mutableStateOf(0)
        compose.setContent { LiZhangTheme {
            ReminderAdvanceDialog(selected.value, {}, { selected.value = it })
        } }
        listOf("当天提醒" to 0, "提前 1 天" to 1, "提前 3 天" to 3, "提前 7 天" to 7).forEach { (label, days) ->
            compose.onNodeWithText(label).performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(days, selected.value) }
        }
    }

    @Test fun 底部四个入口选中状态及重复点击不重复导航() {
        val destination = mutableStateOf<AppDestination>(AppDestination.Home)
        var clicks = 0
        compose.setContent { LiZhangTheme { BottomNavBar(destination.value, { destination.value = it; clicks++ }) } }
        listOf("联系人" to AppDestination.Contacts, "记一笔" to AppDestination.AddGift,
            "我的" to AppDestination.Settings, "首页" to AppDestination.Home).forEach { (label, route) ->
            compose.onNodeWithText(label).performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(route, destination.value) }
        }
        compose.onNodeWithText("首页").performClick()
        compose.runOnIdle { assertEquals(4, clicks) }
    }
}
