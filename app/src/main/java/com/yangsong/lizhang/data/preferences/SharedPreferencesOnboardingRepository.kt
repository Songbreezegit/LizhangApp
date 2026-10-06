package com.yangsong.lizhang.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.domain.onboarding.*
import com.yangsong.lizhang.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 小型引导偏好沿用现有 SharedPreferences；不修改 Room 或备份格式。 */
class SharedPreferencesOnboardingRepository(
    context: Context,
    evidence: ExistingInstallationEvidence = existingInstallationEvidence(context),
) : OnboardingRepository {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState(context, evidence))
    override val state = _state.asStateFlow()

    private fun readState(context: Context, evidence: ExistingInstallationEvidence): OnboardingState {
        val completed = OnboardingPolicy.completed(
            if (preferences.contains(COMPLETED)) preferences.getBoolean(COMPLETED, false) else null,
            evidence,
        )
        val guideStep = FeatureGuidePolicy.initialStep(preferences.getString(FEATURE_GUIDE_STEP, null), completed)
        // 集中迁移，与首次完成标记一起保存；旧版本已完成用户不会突然开始四步引导。
        preferences.edit {
            if (!preferences.contains(COMPLETED)) putBoolean(COMPLETED, completed)
            if (!preferences.contains(FEATURE_GUIDE_STEP)) putString(FEATURE_GUIDE_STEP, guideStep.storedValue)
        }
        return OnboardingState(
            completed = completed,
            featureGuideStep = guideStep,
            homeRecordHintSeen = preferences.getBoolean(HOME_HINT, false),
            contactsHintSeen = preferences.getBoolean(CONTACTS_HINT, false),
            contactsPermission = PermissionHistory(
                preferences.getBoolean(CONTACT_EXPLANATION, false),
                preferences.getBoolean(CONTACT_REQUESTED, false) ||
                    context.getSharedPreferences("contact_permission", Context.MODE_PRIVATE).getBoolean("requested", false),
            ),
            notificationsPermission = PermissionHistory(
                preferences.getBoolean(NOTIFICATION_EXPLANATION, false),
                preferences.getBoolean(NOTIFICATION_REQUESTED, false),
            ),
        )
    }

    @Synchronized override fun complete() {
        preferences.edit { putBoolean(COMPLETED, true) }
        _state.value = _state.value.copy(completed = true)
    }

    @Synchronized override fun advanceFeatureGuide(expectedStep: FeatureGuideStep) {
        val current = _state.value
        if (!current.featureGuideVisible || current.featureGuideStep != expectedStep) return
        saveFeatureGuide(expectedStep.next())
    }

    @Synchronized override fun completeFeatureGuide() {
        if (_state.value.featureGuideVisible) saveFeatureGuide(FeatureGuideStep.COMPLETED)
    }

    private fun saveFeatureGuide(step: FeatureGuideStep) {
        preferences.edit { putString(FEATURE_GUIDE_STEP, step.storedValue) }
        _state.value = _state.value.copy(featureGuideStep = step)
    }

    @Synchronized override fun markHintSeen(hint: ContextualHint) {
        preferences.edit { putBoolean(if (hint == ContextualHint.HOME_RECORD) HOME_HINT else CONTACTS_HINT, true) }
        _state.value = when (hint) {
            ContextualHint.HOME_RECORD -> _state.value.copy(homeRecordHintSeen = true)
            ContextualHint.CONTACTS -> _state.value.copy(contactsHintSeen = true)
        }
    }

    @Synchronized override fun markExplanationSeen(permission: ExplainedPermission) {
        preferences.edit { putBoolean(if (permission == ExplainedPermission.CONTACTS) CONTACT_EXPLANATION else NOTIFICATION_EXPLANATION, true) }
        updatePermission(permission, _state.value.permissionHistory(permission).copy(explanationSeen = true))
    }

    @Synchronized override fun markPermissionRequested(permission: ExplainedPermission) {
        preferences.edit { putBoolean(if (permission == ExplainedPermission.CONTACTS) CONTACT_REQUESTED else NOTIFICATION_REQUESTED, true) }
        updatePermission(permission, _state.value.permissionHistory(permission).copy(requested = true))
    }

    private fun updatePermission(permission: ExplainedPermission, history: PermissionHistory) {
        _state.value = when (permission) {
            ExplainedPermission.CONTACTS -> _state.value.copy(contactsPermission = history)
            ExplainedPermission.NOTIFICATIONS -> _state.value.copy(notificationsPermission = history)
        }
    }

    companion object {
        const val FILE_NAME = "onboarding_preferences"
        private const val COMPLETED = "onboarding_completed"
        private const val FEATURE_GUIDE_STEP = "feature_guide_step"
        private const val HOME_HINT = "home_record_hint_seen"
        private const val CONTACTS_HINT = "contacts_hint_seen"
        private const val CONTACT_EXPLANATION = "contact_permission_explanation_seen"
        private const val NOTIFICATION_EXPLANATION = "notification_permission_explanation_seen"
        private const val CONTACT_REQUESTED = "contact_permission_requested"
        private const val NOTIFICATION_REQUESTED = "notification_permission_requested"
    }
}

/** 必须在旧仓库创建数据库或写入默认偏好之前捕获，避免把全新安装误判为升级。 */
@Suppress("DEPRECATION")
internal fun existingInstallationEvidence(context: Context): ExistingInstallationEvidence {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
    return ExistingInstallationEvidence(
        upgradedInstallation = info.lastUpdateTime > info.firstInstallTime,
        databaseExists = context.getDatabasePath(DatabaseConstants.NAME).exists(),
        existingPreferences = preferencesDirectory.listFiles().orEmpty().any {
            it.extension == "xml" && it.name != "${SharedPreferencesOnboardingRepository.FILE_NAME}.xml"
        },
        savedAppLanguage = File(context.filesDir, "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file").exists(),
    )
}
