package com.yangsong.lizhang.domain.legal

/** 内部同意记录版本。重大正文更新必须变更此标识；不向用户展示为公开法律版本。 */
object LegalPolicy {
    const val CURRENT_VERSION = "legal-product-copy-20261029"
}

enum class LegalDocumentType(val assetName: String, val officialUrl: String) {
    PRIVACY("privacy.md", "https://lizhang.songisle.xyz/privacy/"),
    TERMS("terms.md", "https://lizhang.songisle.xyz/terms/"),
    HELP("help.md", "https://lizhang.songisle.xyz/help/"),
}

data class LegalDocumentSection(val heading: String?, val body: String)

data class LegalDocument(val type: LegalDocumentType, val sections: List<LegalDocumentSection>)

/** 所有语言读取同一完整中文正文；翻译获内容批准后才可替换，不能回退为摘要。 */
object LegalDocumentLanguage {
    const val CONTENT_LANGUAGE = "zh-CN"
    val supportedUiLanguages = setOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")
}

/** 把本地 Markdown 分成可换行卡片；表格逐行展开，避免大字号横向裁切。 */
object LegalDocumentParser {
    fun parse(type: LegalDocumentType, markdown: String): LegalDocument {
        val sections = mutableListOf<LegalDocumentSection>()
        var heading: String? = null
        val lines = mutableListOf<String>()
        fun finishSection() {
            val body = lines.joinToString("\n").trim()
            if (body.isNotBlank()) sections += LegalDocumentSection(heading, body)
            lines.clear()
        }
        markdown.lineSequence().forEach { source ->
            val line = source.trimEnd()
            when {
                line.startsWith("# ") -> Unit
                line.startsWith("## ") -> {
                    finishSection()
                    heading = line.removePrefix("## ")
                }
                line.startsWith("|") && line.contains("---") -> Unit
                line.startsWith("|") -> lines += line.trim().trim('|').split('|')
                    .joinToString("：") { it.trim() }.let(::plainText)
                else -> lines += plainText(line.removePrefix("### "))
            }
        }
        finishSection()
        require(sections.isNotEmpty()) { "离线文档正文为空" }
        return LegalDocument(type, sections)
    }

    private fun plainText(line: String): String {
        val text = line.replace("**", "")
        val output = StringBuilder()
        var offset = 0
        // 仅解析文档使用的 [说明](地址)，避免 Android ICU 与桌面 JVM 的字符类语法差异。
        while (offset < text.length) {
            val start = text.indexOf('[', offset)
            if (start < 0) { output.append(text.substring(offset)); break }
            output.append(text.substring(offset, start))
            val separator = text.indexOf("](", start + 1)
            val end = if (separator >= 0) text.indexOf(')', separator + 2) else -1
            if (separator < 0 || end < 0) {
                output.append('[')
                offset = start + 1
                continue
            }
            output.append(text.substring(start + 1, separator))
            val target = text.substring(separator + 2, end)
            if (target.startsWith("https://")) output.append("（").append(target).append("）")
            offset = end + 1
        }
        return output.toString()
    }
}
