package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.OnboardingRepository
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class FeatureGuideViewModelTest {
    @Test fun `全新安装未完成三页时不显示功能引导`() {
        val state = OnboardingState(featureGuideStep = FeatureGuidePolicy.initialStep(null, false))
        assertEquals(FeatureGuideStep.ADD_RECORD, state.featureGuideStep)
        assertFalse(state.featureGuideVisible)
        val vm = OnboardingViewModel(GuideRepository(state))
        vm.advanceFeatureGuide()
        vm.skipFeatureGuide()
        assertEquals(state, vm.state.value)
    }

    @Test fun `完成或跳过首次三页从记一笔开始`() {
        val vm = OnboardingViewModel(GuideRepository(OnboardingState()))
        vm.finish(OnboardingMode.FIRST_LAUNCH)
        assertTrue(vm.state.value.featureGuideVisible)
        assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
    }

    private fun assertNext(from: FeatureGuideStep, to: FeatureGuideStep) {
        val vm = OnboardingViewModel(GuideRepository(OnboardingState(completed = true, featureGuideStep = from)))
        vm.advanceFeatureGuide()
        assertEquals(to, vm.state.value.featureGuideStep)
    }

    @Test fun `记一笔推进到联系人`() = assertNext(FeatureGuideStep.ADD_RECORD, FeatureGuideStep.CONTACTS)
    @Test fun `联系人推进到提醒`() = assertNext(FeatureGuideStep.CONTACTS, FeatureGuideStep.REMINDERS)
    @Test fun `提醒推进到我的`() = assertNext(FeatureGuideStep.REMINDERS, FeatureGuideStep.SETTINGS)
    @Test fun `我的完成整个引导`() = assertNext(FeatureGuideStep.SETTINGS, FeatureGuideStep.COMPLETED)

    @Test fun `每一步跳过都会永久完成且重复操作幂等`() {
        FeatureGuideStep.entries.forEach { step ->
            val vm = OnboardingViewModel(GuideRepository(OnboardingState(completed = true, featureGuideStep = step)))
            vm.skipFeatureGuide()
            vm.advanceFeatureGuide()
            vm.completeFeatureGuide()
            assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
            assertFalse(vm.state.value.featureGuideVisible)
        }
    }

    @Test fun `四个真实Destination匹配当前步骤才自动前进`() {
        listOf(AppDestination.AddGift, AppDestination.Contacts, AppDestination.Notifications, AppDestination.Settings).forEach { destination ->
            val step = requireNotNull(destination.featureGuideTarget()).step
            val vm = OnboardingViewModel(GuideRepository(OnboardingState(completed = true, featureGuideStep = step)))
            vm.targetInvoked(step)
            assertEquals(step.next(), vm.state.value.featureGuideStep)
        }
    }

    @Test fun `非当前目标和其他Destination不跳步骤`() {
        val vm = OnboardingViewModel(GuideRepository())
        listOf(AppDestination.Contacts, AppDestination.Notifications, AppDestination.Settings).forEach {
            vm.targetInvoked(requireNotNull(it.featureGuideTarget()).step)
            assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
        }
        listOf(AppDestination.Home, AppDestination.Search, AppDestination.ContactImport, AppDestination.Onboarding).forEach {
            assertNull(it.featureGuideTarget())
        }
    }

    @Test fun `相同气泡的重复点击不会越过下一步`() {
        val vm = OnboardingViewModel(GuideRepository())
        repeat(2) { vm.advanceFeatureGuide(FeatureGuideStep.ADD_RECORD) }
        assertEquals(FeatureGuideStep.CONTACTS, vm.state.value.featureGuideStep)
    }

    @Test fun `ViewModel重建沿用仓库中的提醒步骤`() {
        val repository = GuideRepository()
        OnboardingViewModel(repository).apply { advanceFeatureGuide(); advanceFeatureGuide() }
        assertEquals(FeatureGuideStep.REMINDERS, OnboardingViewModel(repository).state.value.featureGuideStep)
    }

    @Test fun `旧版本已完成三页且没有新key的用户跳过四步`() {
        assertEquals(FeatureGuideStep.COMPLETED, FeatureGuidePolicy.initialStep(null, true))
    }

    @Test fun `旧版本未完成三页的用户更新后保持待开始`() {
        val completed = OnboardingPolicy.completed(false, ExistingInstallationEvidence(upgradedInstallation = true, databaseExists = true))
        assertEquals(FeatureGuideStep.ADD_RECORD, FeatureGuidePolicy.initialStep(null, completed))
        assertFalse(OnboardingState(completed = completed).featureGuideVisible)
    }

    @Test fun `缺少首次标记时继续复用旧安装证据`() {
        listOf(ExistingInstallationEvidence(upgradedInstallation = true), ExistingInstallationEvidence(databaseExists = true),
            ExistingInstallationEvidence(existingPreferences = true), ExistingInstallationEvidence(savedAppLanguage = true)).forEach {
            assertEquals(FeatureGuideStep.COMPLETED, FeatureGuidePolicy.initialStep(null, OnboardingPolicy.completed(null, it)))
        }
    }

    @Test fun `已有每种持久步骤永远优先于安装痕迹`() {
        FeatureGuideStep.entries.forEach { step ->
            for (completed in listOf(true, false)) assertEquals(step, FeatureGuidePolicy.initialStep(step.storedValue, completed))
        }
        assertEquals(FeatureGuideStep.COMPLETED, FeatureGuidePolicy.initialStep("未知未来值", false))
    }

    @Test fun `设置手动查看不重置任意功能引导状态`() {
        FeatureGuideStep.entries.forEach { step ->
            val repository = GuideRepository(OnboardingState(completed = true, featureGuideStep = step))
            val before = repository.state.value
            OnboardingViewModel(repository).finish(OnboardingMode.REVIEW)
            assertEquals(before, repository.state.value)
        }
    }

    @Test fun `完成后旧提示未消费或全部礼账删除也不重新显示`() {
        val vm = OnboardingViewModel(GuideRepository())
        vm.completeFeatureGuide()
        // 可见性没有账本参数；旧提示的两个未消费标记也不能重新开启功能引导。
        assertFalse(vm.state.value.copy(homeRecordHintSeen = false, contactsHintSeen = false).featureGuideVisible)
        vm.finish(OnboardingMode.FIRST_LAUNCH)
        assertFalse(vm.state.value.featureGuideVisible)
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
    }

    @Test fun `四步推进不产生任何权限说明或申请历史`() {
        val vm = OnboardingViewModel(GuideRepository())
        repeat(4) { vm.advanceFeatureGuide() }
        assertEquals(PermissionHistory(), vm.state.value.contactsPermission)
        assertEquals(PermissionHistory(), vm.state.value.notificationsPermission)
    }
}

private class GuideRepository(initial: OnboardingState = OnboardingState(completed = true)) : OnboardingRepository {
    override val state = MutableStateFlow(initial)
    override fun complete() { state.value = state.value.copy(completed = true) }
    override fun advanceFeatureGuide(expectedStep: FeatureGuideStep) {
        if (state.value.featureGuideVisible && state.value.featureGuideStep == expectedStep)
            state.value = state.value.copy(featureGuideStep = expectedStep.next())
    }
    override fun completeFeatureGuide() {
        if (state.value.featureGuideVisible) state.value = state.value.copy(featureGuideStep = FeatureGuideStep.COMPLETED)
    }
    override fun markHintSeen(hint: ContextualHint) = Unit
    override fun markExplanationSeen(permission: ExplainedPermission) = Unit
    override fun markPermissionRequested(permission: ExplainedPermission) = Unit
}
