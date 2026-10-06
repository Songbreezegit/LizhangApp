package com.yangsong.lizhang.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.domain.onboarding.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 使用独立的设备保护存储测试偏好持久化，不触碰正式账本或用户设置。 */
class OnboardingPreferencesInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>().createDeviceProtectedStorageContext()
    private val preferences = context.getSharedPreferences(SharedPreferencesOnboardingRepository.FILE_NAME, Context.MODE_PRIVATE)

    @Before fun 清理测试状态() { preferences.edit().clear().commit() }
    @After fun 移除测试状态() { preferences.edit().clear().commit() }

    @Test fun 新安装保存false且不会被后续数据库初始化误判为老用户() {
        val first = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        assertFalse(first.state.value.completed)
        assertTrue(preferences.contains("onboarding_completed"))
        assertFalse(SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(databaseExists = true)).state.value.completed)
    }

    @Test fun 老用户兼容结果持久化且再次启动直接进入业务() {
        assertTrue(SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true)).state.value.completed)
        assertTrue(SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence()).state.value.completed)
    }

    @Test fun 完成和两处提示在新仓库实例中仍然有效() {
        val first = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        first.complete()
        ContextualHint.entries.forEach(first::markHintSeen)
        val second = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        assertTrue(second.state.value.completed)
        ContextualHint.entries.forEach { assertTrue(second.state.value.hintSeen(it)) }
    }

    @Test fun 两种权限说明及请求历史独立持久化() {
        val first = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        first.markExplanationSeen(ExplainedPermission.CONTACTS)
        first.markPermissionRequested(ExplainedPermission.CONTACTS)
        val second = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        assertEquals(PermissionHistory(true, true), second.state.value.contactsPermission)
        assertEquals(PermissionHistory(), second.state.value.notificationsPermission)
        second.markExplanationSeen(ExplainedPermission.NOTIFICATIONS)
        assertFalse(second.state.value.notificationsPermission.requested)
    }

    @Test fun 已完成093用户只迁移新步骤且保留旧key() {
        preferences.edit().putBoolean("onboarding_completed", true)
            .putBoolean("home_record_hint_seen", true).putBoolean("contacts_hint_seen", false).commit()
        val state = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence()).state.value
        assertEquals(FeatureGuideStep.COMPLETED, state.featureGuideStep)
        assertFalse(state.featureGuideVisible)
        assertEquals("completed", preferences.getString("feature_guide_step", null))
        assertTrue(preferences.getBoolean("home_record_hint_seen", false))
        assertTrue(preferences.contains("contacts_hint_seen"))
        assertFalse(preferences.getBoolean("contacts_hint_seen", true))
    }

    @Test fun 未完成093用户即使有升级痕迹也等待三页完成() {
        preferences.edit().putBoolean("onboarding_completed", false).commit()
        val repository = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true))
        assertFalse(repository.state.value.featureGuideVisible)
        assertEquals(FeatureGuideStep.ADD_RECORD, repository.state.value.featureGuideStep)
        repository.complete()
        assertTrue(repository.state.value.featureGuideVisible)
        assertEquals(FeatureGuideStep.ADD_RECORD, repository.state.value.featureGuideStep)
    }

    @Test fun 每步都实际持久化并在仓库及ViewModel重建后恢复() {
        var repository = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
        repository.complete()
        FeatureGuideStep.entries.forEach { step ->
            assertEquals(step, repository.state.value.featureGuideStep)
            repository = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true, databaseExists = true))
            val viewModel = com.yangsong.lizhang.ui.viewmodel.OnboardingViewModel(repository)
            assertEquals(step, viewModel.state.value.featureGuideStep)
            assertEquals(step.storedValue, preferences.getString("feature_guide_step", null))
            viewModel.advanceFeatureGuide(step)
        }
        assertFalse(repository.state.value.featureGuideVisible)
    }

    @Test fun 任一步跳过后新实例均保持完成() {
        FeatureGuideStep.entries.filter { it != FeatureGuideStep.COMPLETED }.forEach { step ->
            preferences.edit().putBoolean("onboarding_completed", true).putString("feature_guide_step", step.storedValue).commit()
            val repository = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence())
            repository.completeFeatureGuide()
            assertEquals(FeatureGuideStep.COMPLETED, SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence()).state.value.featureGuideStep)
        }
    }

    @Test fun 已有新步骤优先于未完成标记和旧安装痕迹() {
        preferences.edit().putBoolean("onboarding_completed", false).putString("feature_guide_step", "reminders").commit()
        val repository = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true))
        assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
        assertFalse(repository.state.value.featureGuideVisible)
        repository.complete()
        assertEquals(FeatureGuideStep.REMINDERS, repository.state.value.featureGuideStep)
    }
}
