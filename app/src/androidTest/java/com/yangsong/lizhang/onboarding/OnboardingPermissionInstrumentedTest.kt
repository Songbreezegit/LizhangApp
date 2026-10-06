package com.yangsong.lizhang.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.*
import com.yangsong.lizhang.ui.screen.*
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 在无权限的独立模拟器验证真实功能入口；测试替身从不读取设备通讯录。 */
class OnboardingPermissionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container get() = (context as LiZhangApplication).appContainer

    @Test fun 主动通讯录导入先说明暂不不会请求系统或读取联系人() {
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS))
        val repository = TestOnboardingRepository()
        val onboarding = OnboardingViewModel(repository)
        var reads = 0
        val importViewModel = ContactImportViewModel(DeviceContactRepository { reads++; emptyList() }, container.contactRepository)
        val importing = mutableStateOf(false)
        compose.setContent { LiZhangTheme {
            if (importing.value) ContactImportScreen(importViewModel, { importing.value = false }, {}, onboarding)
            else ContactsContent(ContactsUiState(isLoading = false), {}, {}, {}, {},
                onImportContacts = { importing.value = true }, initiallyExpanded = true)
        } }
        assertEquals(PermissionHistory(), repository.state.value.contactsPermission)
        compose.onNodeWithText(compose.activity.getString(R.string.contact_import_menu)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.contact_permission_explanation_title)).assertIsDisplayed()
        assertTrue(repository.state.value.contactsPermission.explanationSeen)
        assertFalse(repository.state.value.contactsPermission.requested)
        compose.onNodeWithText(compose.activity.getString(R.string.permission_not_now)).performClick()
        assertFalse(repository.state.value.contactsPermission.requested)
        assertEquals(0, reads)
    }

    @Test fun 主动启用提醒先说明暂不保持关闭() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
        val repository = TestOnboardingRepository()
        container.reminderRepository.setEnabled(false)
        val viewModel = NotificationsViewModel(container.reminderRepository)
        val onboarding = OnboardingViewModel(repository)
        compose.setContent { LiZhangTheme { NotificationsScreen(viewModel, {}, onboarding) } }
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Switch)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.notification_permission_explanation_title)).assertIsDisplayed()
        assertFalse(repository.state.value.notificationsPermission.requested)
        compose.onNodeWithText(compose.activity.getString(R.string.permission_not_now)).performClick()
        assertFalse(container.reminderRepository.settings.value.enabled)
    }

    @Test fun 无运行时通知权限的系统直接启用提醒() {
        assumeTrue(Build.VERSION.SDK_INT < 33)
        val repository = TestOnboardingRepository()
        container.reminderRepository.setEnabled(false)
        val viewModel = NotificationsViewModel(container.reminderRepository)
        val onboarding = OnboardingViewModel(repository)
        compose.setContent { LiZhangTheme {
            NotificationsScreen(viewModel, {}, onboarding)
        } }
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Switch)).performClick()
        compose.waitUntil(5000) { container.reminderRepository.settings.value.enabled }
        assertEquals(PermissionHistory(), repository.state.value.notificationsPermission)
        container.reminderRepository.setEnabled(false)
    }
}

internal class TestOnboardingRepository(initial: OnboardingState = OnboardingState()) : OnboardingRepository {
    override val state = MutableStateFlow(initial)
    override fun complete() { state.value = state.value.copy(completed = true) }
    override fun markHintSeen(hint: ContextualHint) {
        state.value = if (hint == ContextualHint.HOME_RECORD) state.value.copy(homeRecordHintSeen = true)
            else state.value.copy(contactsHintSeen = true)
    }
    override fun markExplanationSeen(permission: ExplainedPermission) = update(permission, state.value.permissionHistory(permission).copy(explanationSeen = true))
    override fun markPermissionRequested(permission: ExplainedPermission) = update(permission, state.value.permissionHistory(permission).copy(requested = true))
    private fun update(permission: ExplainedPermission, history: PermissionHistory) {
        state.value = if (permission == ExplainedPermission.CONTACTS) state.value.copy(contactsPermission = history)
            else state.value.copy(notificationsPermission = history)
    }
}
