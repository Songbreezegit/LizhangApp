package com.yangsong.lizhang.data.legal

import android.content.Context
import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentParser
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.legal.LegalPolicy
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/** 只读取随安装包交付的正文，未读取业务数据，不依赖网络或浏览器。 */
class AssetLegalDocumentRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LegalDocumentRepository {
    private val assets = context.applicationContext.assets

    override suspend fun load(type: LegalDocumentType): LegalDocument = withContext(dispatcher) {
        val markdown = assets.open("legal/${type.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
        // 正文和官网共享同一元数据；缺字段或版本不符时沿用原有加载失败门禁。
        val metadata = Properties().apply {
            assets.open("legal/metadata.properties").reader(Charsets.UTF_8).use { load(it) }
        }
        require(metadata.getProperty("policy_version") == LegalPolicy.CURRENT_VERSION) { "法律资料版本不一致" }
        val values = metadata.stringPropertyNames().associateWith { metadata.getProperty(it) }.toMutableMap()
        values["publication_status"] = if (metadata.getProperty("approval_status") == "approved") {
            "正式文本"
        } else {
            "候选定稿 · 尚未正式生效 · 等待用户验收与发布批准"
        }
        val rendered = Regex("\\{\\{([a-z_]+)}}").replace(markdown) { match ->
            requireNotNull(values[match.groupValues[1]]?.takeIf { it.isNotBlank() }) { "缺少法律资料字段" }
        }
        require(!rendered.contains("{{") && !rendered.contains("}}")) { "法律文档仍有未解析字段" }
        LegalDocumentParser.parse(type, rendered)
    }
}
