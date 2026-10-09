package com.yangsong.lizhang.domain.privacy

import com.yangsong.lizhang.domain.legal.*
import org.junit.Assert.*
import org.junit.Test

class LoadedConsentDocumentsTest {
    private fun document(type: LegalDocumentType) =
        LegalDocument(type, listOf(LegalDocumentSection("合成全文", "测试完整说明")))

    @Test fun `缺失任一全文空正文或错误类型不能形成同意凭据`() {
        val privacy = document(LegalDocumentType.PRIVACY)
        val terms = document(LegalDocumentType.TERMS)
        assertNull(LoadedConsentDocuments.verify(null, terms))
        assertNull(LoadedConsentDocuments.verify(privacy, null))
        assertNull(LoadedConsentDocuments.verify(terms, privacy))
        assertNull(LoadedConsentDocuments.verify(privacy, document(LegalDocumentType.HELP)))
        assertNull(LoadedConsentDocuments.verify(privacy, terms.copy(sections = emptyList())))
        assertNull(LoadedConsentDocuments.verify(privacy.copy(sections = listOf(LegalDocumentSection(null, " "))), terms))
        assertNull(LoadedConsentDocuments.verify(privacy, terms, policyVersion = ""))
    }

    @Test fun `旧政策加载凭据不能用于当前政策确认`() {
        val old = requireNotNull(LoadedConsentDocuments.verify(document(LegalDocumentType.PRIVACY),
            document(LegalDocumentType.TERMS), policyVersion = "旧政策合成版本"))
        assertFalse(old.matchesPolicy(LegalPolicy.CURRENT_VERSION))
        assertTrue(old.matchesPolicy("旧政策合成版本"))
        val current = requireNotNull(LoadedConsentDocuments.verify(document(LegalDocumentType.PRIVACY),
            document(LegalDocumentType.TERMS)))
        assertTrue(current.matchesPolicy(LegalPolicy.CURRENT_VERSION))
        assertFalse(current.matchesPolicy(""))
    }
}
