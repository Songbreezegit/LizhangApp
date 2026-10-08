package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.onboarding.ContextualHint
import com.yangsong.lizhang.domain.onboarding.ExplainedPermission
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.domain.onboarding.FeatureGuidePage
import com.yangsong.lizhang.domain.onboarding.OnboardingState
import kotlinx.coroutines.flow.StateFlow

interface OnboardingRepository {
    val state: StateFlow<OnboardingState>
    fun complete()
    fun advanceFeatureGuide(expectedStep: FeatureGuideStep)
    fun completeFeatureGuide()
    fun completePageGuide(page: FeatureGuidePage) {}
    fun markHintSeen(hint: ContextualHint)
    fun markExplanationSeen(permission: ExplainedPermission)
    fun markPermissionRequested(permission: ExplainedPermission)
}
