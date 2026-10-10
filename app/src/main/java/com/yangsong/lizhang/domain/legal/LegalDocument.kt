package com.yangsong.lizhang.domain.legal

/** 候选内容标识，不代表政策已生效或已获批准。重大内容更新必须变更此标识。 */
object LegalPolicy {
    const val CURRENT_VERSION = "privacy-site-minimal-v1-policy-20261011"
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

    private fun plainText(line: String): String = line.replace("**", "")
        .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)")) { match ->
            val target = match.groupValues[2]
            if (target.startsWith("https://")) "${match.groupValues[1]}（$target）"
            else match.groupValues[1]
        }
}
