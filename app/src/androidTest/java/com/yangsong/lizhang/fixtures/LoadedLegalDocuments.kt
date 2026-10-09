package com.yangsong.lizhang.fixtures

import android.content.Context
import com.yangsong.lizhang.data.legal.AssetLegalDocumentRepository
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.legal.LoadedConsentDocuments
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.runBlocking

/** 业务测试的前置确认仍须加载安装包内两份完整正文，禁止直接写入同意偏好。 */
fun loadedConsentDocuments(context: Context): LoadedConsentDocuments =
    loadedConsentDocuments(AssetLegalDocumentRepository(context))

fun loadedConsentDocuments(repository: LegalDocumentRepository): LoadedConsentDocuments = runBlocking {
    requireNotNull(LoadedConsentDocuments.verify(repository.load(LegalDocumentType.PRIVACY),
        repository.load(LegalDocumentType.TERMS))) { "测试前置所需完整法律文档无法读取" }
}
