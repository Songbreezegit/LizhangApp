package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.OnboardingRepository

class OnboardingViewModel(private val repository: OnboardingRepository) : ViewModel() {
    val state = repository.state

    /** 隐私版本已明确确认后，沿用首页真实记账引导，不把引导完成视为隐私同意。 */
    fun enterApp(privacyAccepted: Boolean) {
        if (privacyAccepted && !state.value.completed) repository.complete()
    }

    fun finish(mode: OnboardingMode) {
        if (mode == OnboardingMode.FIRST_LAUNCH) repository.complete()
    }

    fun advanceFeatureGuide(step: FeatureGuideStep = state.value.featureGuideStep) = repository.advanceFeatureGuide(step)
    fun targetInvoked(step: FeatureGuideStep) = repository.advanceFeatureGuide(step)
    fun skipFeatureGuide() = repository.completeFeatureGuide()
    fun completeFeatureGuide() = repository.completeFeatureGuide()
    fun completePageGuide(page: FeatureGuidePage) = repository.completePageGuide(page)

    fun claimHint(hint: ContextualHint, loaded: Boolean, empty: Boolean): Boolean {
        if (!OnboardingPolicy.shouldShowHint(state.value.hintSeen(hint), loaded, empty)) return false
        // 展示即持久化消费；离开页面、重建或进程重启都不会重复打扰。
        repository.markHintSeen(hint)
        return true
    }

    fun markHintSeen(hint: ContextualHint) = repository.markHintSeen(hint)
    fun permissionHistory(permission: ExplainedPermission) = state.value.permissionHistory(permission)
    fun markExplanationSeen(permission: ExplainedPermission) = repository.markExplanationSeen(permission)
    fun markPermissionRequested(permission: ExplainedPermission) = repository.markPermissionRequested(permission)

    companion object {
        fun factory(repository: OnboardingRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = OnboardingViewModel(repository) as T
        }
    }
}
