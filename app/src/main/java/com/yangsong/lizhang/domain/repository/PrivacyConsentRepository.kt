package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.privacy.PrivacyConsentState
import com.yangsong.lizhang.domain.legal.LoadedConsentDocuments
import kotlinx.coroutines.flow.StateFlow

interface PrivacyConsentRepository {
    val state: StateFlow<PrivacyConsentState>
    /** 两份完整离线文档的当前版本凭据无效或写盘失败时返回 false，不能放行业务处理。 */
    fun acceptCurrentPolicy(documents: LoadedConsentDocuments): Boolean
    fun declineCurrentPolicy(): Boolean
}
