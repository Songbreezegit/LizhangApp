package com.yangsong.lizhang.domain.onboarding

enum class OnboardingMode { FIRST_LAUNCH, REVIEW }
/** v0.9.3 旧偏好兼容，新的首次功能引导不再消费独立提示。 */
enum class ContextualHint { HOME_RECORD, CONTACTS }
enum class ExplainedPermission { CONTACTS, NOTIFICATIONS }

data class PermissionHistory(val explanationSeen: Boolean = false, val requested: Boolean = false)

data class OnboardingState(
    val completed: Boolean = false,
    val featureGuideStep: FeatureGuideStep = FeatureGuideStep.ADD_RECORD,
    val seenPageGuides: Set<FeatureGuidePage> = emptySet(),
    val homeRecordHintSeen: Boolean = false,
    val contactsHintSeen: Boolean = false,
    val contactsPermission: PermissionHistory = PermissionHistory(),
    val notificationsPermission: PermissionHistory = PermissionHistory(),
) {
    val featureGuideVisible get() = completed && featureGuideStep != FeatureGuideStep.COMPLETED

    fun pageGuideVisible(page: FeatureGuidePage) = completed && page !in seenPageGuides

    fun hintSeen(hint: ContextualHint) = when (hint) {
        ContextualHint.HOME_RECORD -> homeRecordHintSeen
        ContextualHint.CONTACTS -> contactsHintSeen
    }

    fun permissionHistory(permission: ExplainedPermission) = when (permission) {
        ExplainedPermission.CONTACTS -> contactsPermission
        ExplainedPermission.NOTIFICATIONS -> notificationsPermission
    }
}

/** 只判断本机使用痕迹，不读取礼金、联系人或备份内容。 */
data class ExistingInstallationEvidence(
    val upgradedInstallation: Boolean = false,
    val databaseExists: Boolean = false,
    val existingPreferences: Boolean = false,
    val savedAppLanguage: Boolean = false,
) {
    val existingUser get() = upgradedInstallation || databaseExists || existingPreferences || savedAppLanguage
}

object OnboardingPolicy {
    // 已保存的 false 必须保留：未完成的新用户更新或重建后仍可继续引导。
    fun completed(saved: Boolean?, evidence: ExistingInstallationEvidence): Boolean = saved ?: evidence.existingUser
    fun shouldShowHint(seen: Boolean, loaded: Boolean, empty: Boolean): Boolean = !seen && loaded && empty
}

enum class PermissionAction { USE_FEATURE, EXPLAIN, REQUEST, OPEN_SETTINGS }

object PermissionPolicy {
    fun action(granted: Boolean, history: PermissionHistory, shouldShowRationale: Boolean): PermissionAction = when {
        granted -> PermissionAction.USE_FEATURE
        history.requested && !shouldShowRationale -> PermissionAction.OPEN_SETTINGS
        !history.explanationSeen || shouldShowRationale -> PermissionAction.EXPLAIN
        else -> PermissionAction.REQUEST
    }
}
