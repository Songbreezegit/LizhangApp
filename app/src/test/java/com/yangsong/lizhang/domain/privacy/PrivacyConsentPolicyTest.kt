package com.yangsong.lizhang.domain.privacy

import com.yangsong.lizhang.domain.legal.LegalPolicy
import org.junit.Assert.*
import org.junit.Test

class PrivacyConsentPolicyTest {
    @Test fun `全新安装没有确认记录必须告知`() {
        assertFalse(PrivacyConsentState().canProcessPersonalData)
    }

    @Test fun `仅当前版本明确确认才能放行`() {
        assertTrue(PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION).canProcessPersonalData)
    }

    @Test fun `旧用户无记录不自动视为同意`() {
        assertFalse(PrivacyConsentPolicy.isAccepted(PrivacyConsentState(), LegalPolicy.CURRENT_VERSION))
    }

    @Test fun `重大政策更新后旧确认不能放行`() {
        val state = PrivacyConsentState(acceptedVersion = "以前的政策")
        assertFalse(state.canProcessPersonalData)
        assertTrue(PrivacyConsentPolicy.isAccepted(state, "以前的政策"))
    }

    @Test fun `拒绝当前版本优先于历史同意`() {
        val state = PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION, declinedVersion = LegalPolicy.CURRENT_VERSION)
        assertFalse(state.canProcessPersonalData)
    }

    @Test fun `拒绝旧版本不会阻止当前明确确认且空版本不能放行`() {
        assertTrue(PrivacyConsentState(acceptedVersion = LegalPolicy.CURRENT_VERSION, declinedVersion = "旧版本").canProcessPersonalData)
        assertFalse(PrivacyConsentPolicy.isAccepted(PrivacyConsentState(acceptedVersion = ""), ""))
    }
}
