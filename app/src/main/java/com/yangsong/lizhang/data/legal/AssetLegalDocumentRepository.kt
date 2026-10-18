package com.yangsong.lizhang.data.legal

import android.content.Context
import android.util.Log
import com.yangsong.lizhang.BuildConfig
import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentParser
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.domain.repository.LegalDocumentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** 只读取随安装包交付的正文，未读取业务数据，不依赖网络或浏览器。 */
class AssetLegalDocumentRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LegalDocumentRepository {
    private val assets = context.applicationContext.assets

    override suspend fun load(type: LegalDocumentType): LegalDocument = withContext(dispatcher) {
        var stage = "读取资源"
        try {
            // 模板和版本在构建时处理；安装包中的这个路径已是完整正文。
            val markdown = assets.open("legal/${type.assetName}").bufferedReader(Charsets.UTF_8).use { it.readText() }
            stage = "检查正文"
            require(markdown.isNotBlank() && !markdown.contains("{{") && !markdown.contains("}}")) { "离线正文资源不完整" }
            stage = "解析正文"
            LegalDocumentParser.parse(type, markdown)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) {
                val frame = error.stackTrace.firstOrNull { it.className.startsWith("com.yangsong.lizhang.") }
                // 仅固定文档类型、阶段、错误类型和应用源码位置；不输出 message、堆栈或正文。
                Log.w("LiZhangLegal", "离线文档失败 document=${type.name} stage=$stage error=${error.javaClass.simpleName} source=${frame?.fileName}:${frame?.lineNumber}")
            }
            throw error
        }
    }
}
