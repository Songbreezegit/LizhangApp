package com.yangsong.lizhang.data.legal

import android.content.Context
import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentParser
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只读取随安装包交付的正文，未读取业务数据，不依赖网络或浏览器。 */
class AssetLegalDocumentRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LegalDocumentRepository {
    private val assets = context.applicationContext.assets

    override suspend fun load(type: LegalDocumentType): LegalDocument = withContext(dispatcher) {
        val markdown = assets.open("legal/${type.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
        LegalDocumentParser.parse(type, markdown)
    }
}
