package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import kotlinx.coroutines.flow.StateFlow

interface PrivacyConsentRepository {
    val state: StateFlow<PrivacyConsentState>
    /** 写盘失败时返回 false，不能放行业务处理。 */
    fun acceptCurrentPolicy(): Boolean
    fun declineCurrentPolicy(): Boolean
}
