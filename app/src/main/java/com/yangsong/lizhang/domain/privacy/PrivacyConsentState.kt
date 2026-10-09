package com.yangsong.lizhang.domain.privacy

import com.yangsong.lizhang.domain.legal.LegalPolicy

/** 只保存用户明确选择及内容版本；旧引导完成状态不等于隐私确认。 */
data class PrivacyConsentState(
    val acceptedVersion: String? = null,
    val declinedVersion: String? = null,
) {
    val canProcessPersonalData: Boolean
        get() = PrivacyConsentPolicy.isAccepted(this, LegalPolicy.CURRENT_VERSION)
}

object PrivacyConsentPolicy {
    /** 版本必须完全相同；重大内容更新后必须再次明确告知。 */
    fun isAccepted(state: PrivacyConsentState, requiredVersion: String): Boolean =
        requiredVersion.isNotBlank() && state.acceptedVersion == requiredVersion &&
            state.declinedVersion != requiredVersion
}
