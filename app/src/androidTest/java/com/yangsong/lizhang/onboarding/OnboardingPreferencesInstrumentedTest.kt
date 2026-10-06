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
}
