package com.yangsong.lizhang.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.data.preferences.SharedPreferencesOnboardingRepository
import com.yangsong.lizhang.data.preferences.SharedPreferencesPrivacyConsentRepository
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.onboarding.ExistingInstallationEvidence
import com.yangsong.lizhang.domain.onboarding.FeatureGuidePage
import com.yangsong.lizhang.domain.onboarding.FeatureGuideStep
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** 独立设备保护存储仅包含合成测试偏好，不访问正式账本。 */
class PrivacyConsentPreferencesInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>().createDeviceProtectedStorageContext()
    private val preferences = context.getSharedPreferences(SharedPreferencesPrivacyConsentRepository.FILE_NAME, Context.MODE_PRIVATE)
    private val onboardingPreferences = context.getSharedPreferences(SharedPreferencesOnboardingRepository.FILE_NAME, Context.MODE_PRIVATE)
    @Before fun 清理() { preferences.edit().clear().commit(); onboardingPreferences.edit().clear().commit() }
    @After fun 收尾() { preferences.edit().clear().commit(); onboardingPreferences.edit().clear().commit() }

    @Test fun 首次没有同意拒绝和重新实例化均保持告知() {
        val first = SharedPreferencesPrivacyConsentRepository(context)
        assertFalse(first.state.value.canProcessPersonalData)
        assertTrue(first.declineCurrentPolicy())
        val rebuilt = SharedPreferencesPrivacyConsentRepository(context)
        assertEquals(LegalPolicy.CURRENT_VERSION, rebuilt.state.value.declinedVersion)
        assertNull(rebuilt.state.value.acceptedVersion)
        assertFalse(rebuilt.state.value.canProcessPersonalData)
    }

    @Test fun 明确确认版本同步写盘后新实例继续放行() {
        val first = SharedPreferencesPrivacyConsentRepository(context)
        first.declineCurrentPolicy()
        assertTrue(first.acceptCurrentPolicy())
        val rebuilt = SharedPreferencesPrivacyConsentRepository(context)
        assertTrue(rebuilt.state.value.canProcessPersonalData)
        assertEquals(LegalPolicy.CURRENT_VERSION, rebuilt.state.value.acceptedVersion)
        assertNull(rebuilt.state.value.declinedVersion)
    }

    @Test fun 旧版本同意需再次告知拒绝后保留旧版本确认记录() {
        preferences.edit().putString(SharedPreferencesPrivacyConsentRepository.ACCEPTED_VERSION, "旧政策合成版本").commit()
        val repository = SharedPreferencesPrivacyConsentRepository(context)
        assertFalse(repository.state.value.canProcessPersonalData)
        assertTrue(repository.declineCurrentPolicy())
        val rebuilt = SharedPreferencesPrivacyConsentRepository(context)
        assertEquals("旧政策合成版本", rebuilt.state.value.acceptedVersion)
        assertEquals(LegalPolicy.CURRENT_VERSION, rebuilt.state.value.declinedVersion)
        assertFalse(rebuilt.state.value.canProcessPersonalData)
    }

    @Test fun 升级用户引导与权限历史原样保留且不自动同意() {
        onboardingPreferences.edit().putBoolean("onboarding_completed", true)
            .putString("feature_guide_step", FeatureGuideStep.RECORD_AMOUNT.storedValue)
            .putStringSet("feature_guide_pages_seen", setOf(FeatureGuidePage.CONTACTS.name))
            .putBoolean("contact_permission_requested", true).commit()
        val before = SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true)).state.value
        val consent = SharedPreferencesPrivacyConsentRepository(context)
        assertFalse(consent.state.value.canProcessPersonalData)
        assertTrue(consent.declineCurrentPolicy())
        assertEquals(before, SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true)).state.value)
        assertTrue(consent.acceptCurrentPolicy())
        assertEquals(before, SharedPreferencesOnboardingRepository(context, ExistingInstallationEvidence(upgradedInstallation = true)).state.value)
    }
}
