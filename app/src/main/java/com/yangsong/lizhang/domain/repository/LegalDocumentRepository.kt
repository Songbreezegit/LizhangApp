package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentType

fun interface LegalDocumentRepository {
    suspend fun load(type: LegalDocumentType): LegalDocument
}
