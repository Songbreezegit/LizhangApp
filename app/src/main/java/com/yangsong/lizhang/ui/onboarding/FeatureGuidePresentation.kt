package com.yangsong.lizhang.ui.onboarding

import com.yangsong.lizhang.domain.onboarding.FeatureGuidePage
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import com.yangsong.lizhang.domain.onboarding.OnboardingState
import com.yangsong.lizhang.ui.navigation.AppDestination

data class FeatureGuidePresentation(
    val step: FeatureGuideStep,
    val target: FeatureGuideTarget,
    val page: FeatureGuidePage? = null,
)

/** 只按当前真实页面选择介绍，不为了引导跳转到未打开的功能。 */
fun featureGuidePresentation(state: OnboardingState, route: String?): FeatureGuidePresentation? {
    if (!state.completed) return null
    if (state.featureGuideVisible && route == AppDestination.Home.route) {
        return FeatureGuidePresentation(FeatureGuideStep.ADD_RECORD, FeatureGuideTarget.ADD_RECORD)
    }
    if (state.featureGuideVisible && route in recordGuideRoutes) {
        val step = state.featureGuideStep.takeIf { it != FeatureGuideStep.ADD_RECORD } ?: return null
        return FeatureGuideTarget.entries.firstOrNull { it.step == step }
            ?.let { FeatureGuidePresentation(step, it) }
    }
    val page = when (route) {
        AppDestination.Contacts.route -> FeatureGuidePage.CONTACTS
        AppDestination.Notifications.route -> FeatureGuidePage.REMINDERS
        AppDestination.Settings.route -> FeatureGuidePage.SETTINGS
        AppDestination.Calendar.route -> FeatureGuidePage.CALENDAR
        AppDestination.Search.route -> FeatureGuidePage.SEARCH
        AppDestination.Statistics.route -> FeatureGuidePage.STATISTICS
        else -> return null
    }
    if (!state.pageGuideVisible(page)) return null
    val target = when (page) {
        FeatureGuidePage.CONTACTS -> FeatureGuideTarget.CONTACTS_PAGE
        FeatureGuidePage.REMINDERS -> FeatureGuideTarget.REMINDERS_PAGE
        FeatureGuidePage.SETTINGS -> FeatureGuideTarget.SETTINGS_PAGE
        FeatureGuidePage.CALENDAR -> FeatureGuideTarget.CALENDAR
        FeatureGuidePage.SEARCH -> FeatureGuideTarget.SEARCH
        FeatureGuidePage.STATISTICS -> FeatureGuideTarget.STATISTICS
    }
    return FeatureGuidePresentation(page.step, target, page)
}

val recordGuideRoutes = setOf(AppDestination.AddGift.route, AppDestination.AddGiftForContact.route)
