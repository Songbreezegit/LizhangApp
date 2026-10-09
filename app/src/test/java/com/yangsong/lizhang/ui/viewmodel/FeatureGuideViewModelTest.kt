package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.OnboardingRepository
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuidePresentation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class FeatureGuideViewModelTest {
    @Test fun `隐私确认后进入首页记账介绍而不先展示三页`() {
        val vm = OnboardingViewModel(GuideRepository(OnboardingState()))
        assertNull(featureGuidePresentation(vm.state.value, AppDestination.Home.route))
        vm.enterApp(privacyAccepted = true)
        assertTrue(vm.state.value.completed)
        assertEquals(FeatureGuideTarget.ADD_RECORD, featureGuidePresentation(vm.state.value, AppDestination.Home.route)?.target)
        assertEquals(emptySet<FeatureGuidePage>(), vm.state.value.seenPageGuides)
    }

    @Test fun `主引导只介绍记账且不会进入其他功能`() {
        val vm = OnboardingViewModel(GuideRepository())
        val steps = listOf(FeatureGuideStep.ADD_RECORD, FeatureGuideStep.RECORD_CONTACT,
            FeatureGuideStep.RECORD_AMOUNT, FeatureGuideStep.RECORD_DIRECTION, FeatureGuideStep.RECORD_SAVE)
        steps.forEach { step ->
            assertEquals(step, vm.state.value.featureGuideStep)
            vm.advanceFeatureGuide(step)
        }
        assertEquals(FeatureGuideStep.COMPLETED, vm.state.value.featureGuideStep)
        assertFalse(vm.state.value.featureGuideVisible)
        assertEquals(emptySet<FeatureGuidePage>(), vm.state.value.seenPageGuides)
    }

    @Test fun `跳过记账引导仍可首次进入其他功能时获得介绍`() {
        val vm = OnboardingViewModel(GuideRepository())
        vm.skipFeatureGuide()
        assertNull(featureGuidePresentation(vm.state.value, AppDestination.Home.route))
        assertNotNull(featureGuidePresentation(vm.state.value, AppDestination.Contacts.route))
        assertNotNull(featureGuidePresentation(vm.state.value, AppDestination.Settings.route))
    }

    @Test fun `未打开的页面不出现在首页介绍中`() {
        val state = OnboardingState(completed = true, featureGuideStep = FeatureGuideStep.COMPLETED)
        assertNull(featureGuidePresentation(state, AppDestination.Home.route))
        assertNull(featureGuidePresentation(state, AppDestination.ContactDetail.route))
        assertNull(featureGuidePresentation(state, AppDestination.GiftRecordEditor.route))
        assertNull(featureGuidePresentation(state, AppDestination.GuidePractice.route))
    }

    @Test fun `其他功能只匹配各自首次进入页面的真实控件`() {
        val pairs = listOf(
            AppDestination.Contacts to FeatureGuideTarget.CONTACTS_PAGE,
            AppDestination.Notifications to FeatureGuideTarget.REMINDERS_PAGE,
            AppDestination.Settings to FeatureGuideTarget.SETTINGS_PAGE,
            AppDestination.Calendar to FeatureGuideTarget.CALENDAR,
            AppDestination.Search to FeatureGuideTarget.SEARCH,
            AppDestination.Statistics to FeatureGuideTarget.STATISTICS,
        )
        pairs.forEach { (destination, target) ->
            val vm = OnboardingViewModel(GuideRepository())
            val presentation = requireNotNull(featureGuidePresentation(vm.state.value, destination.route))
            assertEquals(target, presentation.target)
            vm.completePageGuide(requireNotNull(presentation.page))
            assertNull(featureGuidePresentation(vm.state.value, destination.route))
            assertEquals(FeatureGuideStep.ADD_RECORD, vm.state.value.featureGuideStep)
            assertEquals(1, vm.state.value.seenPageGuides.size)
        }
    }

    @Test fun `完成联系人介绍不会消费提醒或备份介绍`() {
        val vm = OnboardingViewModel(GuideRepository())
        vm.completePageGuide(FeatureGuidePage.CONTACTS)
        repeat(2) { vm.completePageGuide(FeatureGuidePage.CONTACTS) }
        assertEquals(setOf(FeatureGuidePage.CONTACTS), vm.state.value.seenPageGuides)
        assertNotNull(featureGuidePresentation(vm.state.value, AppDestination.Notifications.route))
        assertNotNull(featureGuidePresentation(vm.state.value, AppDestination.Settings.route))
    }

    @Test fun `介绍尚未启动时不能消费任何步骤`() {
        val vm = OnboardingViewModel(GuideRepository(OnboardingState()))
        vm.advanceFeatureGuide()
        vm.skipFeatureGuide()
        vm.completePageGuide(FeatureGuidePage.CONTACTS)
        assertEquals(OnboardingState(), vm.state.value)
    }

    @Test fun `重复点击同一步不会越过后续记账步骤`() {
        val vm = OnboardingViewModel(GuideRepository())
        repeat(2) { vm.advanceFeatureGuide(FeatureGuideStep.ADD_RECORD) }
        assertEquals(FeatureGuideStep.RECORD_CONTACT, vm.state.value.featureGuideStep)
        vm.targetInvoked(FeatureGuideStep.SETTINGS)
        assertEquals(FeatureGuideStep.RECORD_CONTACT, vm.state.value.featureGuideStep)
    }

    @Test fun `新建联系人或离开页面时保留记账步骤并可从首页继续`() {
        val vm = OnboardingViewModel(GuideRepository())
        vm.advanceFeatureGuide()
        vm.advanceFeatureGuide()
        assertEquals(FeatureGuideStep.RECORD_AMOUNT, vm.state.value.featureGuideStep)
        assertNull(featureGuidePresentation(vm.state.value, AppDestination.ContactEditor.route))
        assertEquals(FeatureGuideTarget.ADD_RECORD, featureGuidePresentation(vm.state.value, AppDestination.Home.route)?.target)
        assertEquals(FeatureGuideTarget.RECORD_AMOUNT, featureGuidePresentation(vm.state.value, AppDestination.AddGift.route)?.target)
        assertEquals(FeatureGuideTarget.RECORD_AMOUNT, featureGuidePresentation(vm.state.value, AppDestination.AddGiftForContact.route)?.target)
    }

    @Test fun `ViewModel重建保留独立页面介绍与记账进度`() {
        val repository = GuideRepository()
        OnboardingViewModel(repository).apply {
            advanceFeatureGuide()
            advanceFeatureGuide()
            completePageGuide(FeatureGuidePage.SEARCH)
        }
        val rebuilt = OnboardingViewModel(repository)
        rebuilt.enterApp(privacyAccepted = true)
        assertEquals(FeatureGuideStep.RECORD_AMOUNT, rebuilt.state.value.featureGuideStep)
        assertNull(featureGuidePresentation(rebuilt.state.value, AppDestination.Search.route))
        assertNotNull(featureGuidePresentation(rebuilt.state.value, AppDestination.Calendar.route))
    }

    @Test fun `已完成的升级用户缺少新页面key不会被强制重播`() {
        for (saved in listOf(null, FeatureGuideStep.COMPLETED.storedValue, "未知未来值")) {
            assertEquals(FeatureGuideStep.COMPLETED, FeatureGuidePolicy.initialStep(saved, true))
            assertEquals(FeatureGuidePage.entries.toSet(), FeatureGuidePolicy.initialSeenPages(null, saved, true))
        }
    }

    @Test fun `新用户未完成状态更新后继续真实记账`() {
        assertEquals(FeatureGuideStep.ADD_RECORD, FeatureGuidePolicy.initialStep(null, false))
        assertEquals(emptySet<FeatureGuidePage>(), FeatureGuidePolicy.initialSeenPages(null, null, false))
        FeatureGuideStep.recordSteps.forEach { step ->
            assertEquals(step, FeatureGuidePolicy.initialStep(step.storedValue, true))
        }
    }

    @Test fun `旧四步尚未完成其他功能时不在首页强迫串行介绍`() {
        for (step in listOf(FeatureGuideStep.CONTACTS, FeatureGuideStep.REMINDERS, FeatureGuideStep.SETTINGS)) {
            assertEquals(FeatureGuideStep.COMPLETED, FeatureGuidePolicy.initialStep(step.storedValue, true))
            assertEquals(emptySet<FeatureGuidePage>(), FeatureGuidePolicy.initialSeenPages(null, step.storedValue, true))
        }
    }

    @Test fun `已持久化的页面介绍状态优先于迁移默认值`() {
        assertEquals(setOf(FeatureGuidePage.CALENDAR, FeatureGuidePage.CONTACTS),
            FeatureGuidePolicy.initialSeenPages(setOf("CALENDAR", "CONTACTS", "未来功能"), "completed", true))
        assertEquals(emptySet<FeatureGuidePage>(), FeatureGuidePolicy.initialSeenPages(emptySet(), "completed", true))
    }

    @Test fun `设置手动查看不重置首次状态或消费页面介绍`() {
        val repository = GuideRepository(OnboardingState(completed = true,
            featureGuideStep = FeatureGuideStep.RECORD_SAVE, seenPageGuides = setOf(FeatureGuidePage.CONTACTS)))
        val before = repository.state.value
        OnboardingViewModel(repository).finish(OnboardingMode.REVIEW)
        assertEquals(before, repository.state.value)
    }

    @Test fun `完成和重进不影响权限历史也不因空账本重新介绍`() {
        val vm = OnboardingViewModel(GuideRepository())
        vm.completeFeatureGuide()
        FeatureGuidePage.entries.forEach(vm::completePageGuide)
        vm.enterApp(privacyAccepted = true)
        assertFalse(vm.state.value.featureGuideVisible)
        assertFalse(vm.state.value.copy(homeRecordHintSeen = false, contactsHintSeen = false).featureGuideVisible)
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
    override fun completePageGuide(page: FeatureGuidePage) {
        if (state.value.pageGuideVisible(page)) state.value = state.value.copy(seenPageGuides = state.value.seenPageGuides + page)
    }
    override fun markHintSeen(hint: ContextualHint) = Unit
    override fun markExplanationSeen(permission: ExplainedPermission) = Unit
    override fun markPermissionRequested(permission: ExplainedPermission) = Unit
}
