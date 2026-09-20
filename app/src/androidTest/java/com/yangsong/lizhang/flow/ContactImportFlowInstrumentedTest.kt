package com.yangsong.lizhang.flow

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.data.di.AppContainer
import com.yangsong.lizhang.domain.model.DeviceContact
import com.yangsong.lizhang.domain.model.ContactImportStatus
import com.yangsong.lizhang.domain.repository.DeviceContactRepository
import com.yangsong.lizhang.ui.navigation.LiZhangNavGraph
import com.yangsong.lizhang.ui.screen.ContactImportContent
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactImportFlowInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun 拒绝及永久拒绝显示对应操作且不影响返回() {
        val state = androidx.compose.runtime.mutableStateOf(ContactImportUiState(permissionState = ContactPermissionState.DENIED))
        var back = false
        var permission = false
        compose.setContent { LiZhangTheme {
            ContactImportContent(state.value, { back = true }, {}, {}, {}, {}, {}, { permission = true })
        } }
        compose.onNodeWithText("重新授权").performClick()
        assertTrue(permission)
        compose.runOnIdle { state.value = state.value.copy(permissionState = ContactPermissionState.BLOCKED) }
        compose.onNodeWithText("去设置").assertIsDisplayed()
        compose.onNodeWithText("返回联系人").performClick()
        assertTrue(back)
    }

    @Test fun 空通讯录显示提示且不能导入() {
        compose.setContent { LiZhangTheme {
            ContactImportContent(ContactImportUiState(permissionState = ContactPermissionState.GRANTED, loaded = true), {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("手机通讯录中暂无可导入联系人").assertIsDisplayed()
        compose.onNodeWithText("导入 0 位联系人").assertIsNotEnabled()
    }

    @Test fun 同名联系人显示提示默认不选且允许手动选择() {
        val row = ContactImportCandidate(DeviceContact(1, "同名测试", "13800000000"), ContactImportStatus.POSSIBLE_DUPLICATE)
        val selected = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { LiZhangTheme {
            ContactImportContent(ContactImportUiState(
                permissionState = ContactPermissionState.GRANTED, loaded = true,
                contacts = listOf(row), visibleContacts = listOf(row),
                selectedKeys = if (selected.value) setOf(row.contact.phone) else emptySet(),
            ), {}, {}, { selected.value = !selected.value }, {}, {}, {}, {})
        } }
        compose.onNodeWithText("同名联系人").assertIsDisplayed()
        compose.onNodeWithText("导入 0 位联系人").assertIsNotEnabled()
        compose.onNodeWithText("同名测试").assertIsEnabled().performClick()
        compose.onNodeWithText("导入 1 位联系人").assertIsEnabled()
    }

    @Test fun 已存在联系人不可选且长姓名和号码脱敏() {
        val existing = ContactImportCandidate(DeviceContact(1, "已存在测试", "13800000000"), ContactImportStatus.EXISTING)
        val fresh = ContactImportCandidate(DeviceContact(2, "长姓名测试".repeat(30), "+12025550123"), ContactImportStatus.NEW)
        compose.setContent { LiZhangTheme {
            ContactImportContent(ContactImportUiState(
                permissionState = ContactPermissionState.GRANTED, loaded = true,
                contacts = listOf(existing, fresh), visibleContacts = listOf(existing, fresh), selectedKeys = setOf(fresh.contact.phone),
            ), {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("已存在测试").assertIsNotEnabled()
        compose.onNodeWithText("138****0000").assertExists()
        compose.onNodeWithText("13800000000").assertDoesNotExist()
        compose.onNodeWithText("导入 1 位联系人").assertIsEnabled()
    }

    @Test fun 联系人入口授权后通过假通讯录导入并返回自动刷新() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.READ_CONTACTS)
        val container = AppContainer(context, DeviceContactRepository { listOf(DeviceContact(1, "流程导入虚构测试", "+12025550123")) })
        runBlocking { container.contactRepository.observeContacts().first().filter { it.phone == "+12025550123" }.forEach { container.contactRepository.delete(it) } }
        try {
            compose.setContent { LiZhangTheme { LiZhangNavGraph(container) } }
            compose.onNodeWithText("联系人").performClick()
            compose.onNodeWithText("从通讯录导入").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("导入 1 位联系人").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("导入 1 位联系人").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("已导入 1 位联系人，跳过 0 位已存在联系人，0 位可能重复联系人未选择").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("流程导入虚构测试").performScrollTo().assertIsDisplayed()
        } finally {
            runBlocking { container.contactRepository.observeContacts().first().filter { it.phone == "+12025550123" }.forEach { container.contactRepository.delete(it) } }
        }
    }
}
