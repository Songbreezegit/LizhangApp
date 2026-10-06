package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {
    @Test fun `全新安装默认展示引导`() {
        assertFalse(OnboardingPolicy.completed(null, ExistingInstallationEvidence()))
    }

    @Test fun `已完成的用户不再展示引导`() {
        assertTrue(OnboardingPolicy.completed(true, ExistingInstallationEvidence()))
    }

    @Test fun `升级安装及每种旧使用痕迹均跳过首次引导`() {
        listOf(
            ExistingInstallationEvidence(upgradedInstallation = true),
            ExistingInstallationEvidence(databaseExists = true),
            ExistingInstallationEvidence(existingPreferences = true),
            ExistingInstallationEvidence(savedAppLanguage = true),
        ).forEach { assertTrue(OnboardingPolicy.completed(null, it)) }
    }

    @Test fun `未完成新用户重建或更新仍继续首次引导`() {
        assertFalse(OnboardingPolicy.completed(false, ExistingInstallationEvidence(upgradedInstallation = true, databaseExists = true)))
    }

    @Test fun `点击跳过保存完成状态`() {
        val repository = FakeOnboardingRepository()
        OnboardingViewModel(repository).finish(OnboardingMode.FIRST_LAUNCH)
        assertTrue(repository.state.value.completed)
        assertEquals(1, repository.completions)
    }

    @Test fun `点击开始使用保存完成状态且重复操作幂等`() {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        repeat(2) { viewModel.finish(OnboardingMode.FIRST_LAUNCH) }
        assertTrue(repository.state.value.completed)
    }

    @Test fun `手动重新查看及完成不影响任意首次状态`() {
        for (completed in listOf(false, true)) {
            val repository = FakeOnboardingRepository(OnboardingState(completed = completed))
            OnboardingViewModel(repository).finish(OnboardingMode.REVIEW)
            assertEquals(completed, repository.state.value.completed)
            assertEquals(0, repository.completions)
        }
    }

    @Test fun `未加载错误及已有内容时不消费提示`() {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        for (hint in ContextualHint.entries) {
            assertFalse(viewModel.claimHint(hint, loaded = false, empty = true))
            assertFalse(viewModel.claimHint(hint, loaded = true, empty = false))
            assertFalse(repository.state.value.hintSeen(hint))
        }
    }

    @Test fun `提示只消费一次且新ViewModel不重复展示`() {
        val repository = FakeOnboardingRepository()
        for (hint in ContextualHint.entries) {
            assertTrue(OnboardingViewModel(repository).claimHint(hint, loaded = true, empty = true))
            assertFalse(OnboardingViewModel(repository).claimHint(hint, loaded = true, empty = true))
        }
    }

    @Test fun `点击记一笔后消费首页提示而不影响联系人提示`() {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        viewModel.markHintSeen(ContextualHint.HOME_RECORD)
        assertFalse(viewModel.claimHint(ContextualHint.HOME_RECORD, true, true))
        assertTrue(viewModel.claimHint(ContextualHint.CONTACTS, true, true))
    }

    @Test fun `首次权限操作先说明再次明确操作可以请求`() {
        for (permission in ExplainedPermission.entries) {
            val viewModel = OnboardingViewModel(FakeOnboardingRepository())
            assertEquals(PermissionAction.EXPLAIN, PermissionPolicy.action(false, viewModel.permissionHistory(permission), false))
            viewModel.markExplanationSeen(permission)
            assertEquals(PermissionAction.REQUEST, PermissionPolicy.action(false, viewModel.permissionHistory(permission), false))
        }
    }

    @Test fun `拒绝后需说明而永久拒绝提供设置入口`() {
        val history = PermissionHistory(explanationSeen = true, requested = true)
        assertEquals(PermissionAction.EXPLAIN, PermissionPolicy.action(false, history, true))
        assertEquals(PermissionAction.OPEN_SETTINGS, PermissionPolicy.action(false, history, false))
    }

    @Test fun `已有权限或系统无需运行时权限直接使用功能`() {
        assertEquals(PermissionAction.USE_FEATURE, PermissionPolicy.action(true, PermissionHistory(), false))
    }

    @Test fun `完成引导和提示不会记录任何权限请求`() {
        val repository = FakeOnboardingRepository()
        val viewModel = OnboardingViewModel(repository)
        viewModel.finish(OnboardingMode.FIRST_LAUNCH)
        ContextualHint.entries.forEach { viewModel.claimHint(it, true, true) }
        ExplainedPermission.entries.forEach { assertEquals(PermissionHistory(), viewModel.permissionHistory(it)) }
    }
}

private class FakeOnboardingRepository(initial: OnboardingState = OnboardingState()) : OnboardingRepository {
    override val state = MutableStateFlow(initial)
    var completions = 0
    override fun complete() { completions++; state.value = state.value.copy(completed = true) }
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
