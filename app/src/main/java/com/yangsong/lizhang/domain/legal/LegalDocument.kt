package com.yangsong.lizhang.domain.legal

/** 内部同意记录版本。重大正文更新必须变更此标识；不向用户展示为公开法律版本。 */
object LegalPolicy {
    const val CURRENT_VERSION = "legal-final-v1.0.0-20261103"
}

enum class LegalDocumentType(val assetName: String, val officialUrl: String) {
    PRIVACY("privacy.md", "https://lizhang.songisle.xyz/privacy/"),
    TERMS("terms.md", "https://lizhang.songisle.xyz/terms/"),
    HELP("help.md", "https://lizhang.songisle.xyz/help/"),
}

sealed interface LegalDocumentBlock {
    val text: String

    data class Paragraph(override val text: String) : LegalDocumentBlock
    data class Heading(override val text: String, val level: Int) : LegalDocumentBlock
    data class ListItem(override val text: String, val marker: String) : LegalDocumentBlock
}

data class LegalDocumentSection(
    val heading: String?,
    val body: String,
    val blocks: List<LegalDocumentBlock> = listOf(LegalDocumentBlock.Paragraph(body)),
)

data class LegalDocument(val type: LegalDocumentType, val sections: List<LegalDocumentSection>)

/** 所有语言读取同一完整中文正文；翻译获内容批准后才可替换，不能回退为摘要。 */
object LegalDocumentLanguage {
    const val CONTENT_LANGUAGE = "zh-CN"
    val supportedUiLanguages = setOf("zh-CN", "zh-Hant", "en", "ja", "ko", "es", "fr")
}

/** 保留本地文档的章节、小标题、段落与列表；表格逐行展开，避免横向裁切。 */
object LegalDocumentParser {
    private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
    private val unorderedItemPattern = Regex("^[-+*]\\s+(.+)$")
    private val orderedItemPattern = Regex("^(\\d+)[.)]\\s+(.+)$")
    private val inlineCodePattern = Regex("`([^`]+)`")

    fun parse(type: LegalDocumentType, markdown: String): LegalDocument {
        val sections = mutableListOf<LegalDocumentSection>()
        var heading: String? = null
        val blocks = mutableListOf<LegalDocumentBlock>()
        val paragraphLines = mutableListOf<String>()
        fun finishParagraph() {
            val text = paragraphLines.joinToString("\n").trim()
            if (text.isNotBlank()) blocks += LegalDocumentBlock.Paragraph(text)
            paragraphLines.clear()
        }
        fun finishSection() {
            finishParagraph()
            val body = blocks.joinToString("\n\n") {
                if (it is LegalDocumentBlock.ListItem) "${it.marker} ${it.text}" else it.text
            }
            if (body.isNotBlank()) sections += LegalDocumentSection(heading, body, blocks.toList())
            blocks.clear()
        }
        markdown.lineSequence().forEach { source ->
            val line = source.trimEnd()
            val title = headingPattern.matchEntire(line)
            val unorderedItem = unorderedItemPattern.matchEntire(line)
            val orderedItem = orderedItemPattern.matchEntire(line)
            when {
                title != null && title.groupValues[1].length == 1 -> Unit
                title != null && title.groupValues[1].length == 2 -> {
                    finishSection()
                    heading = plainText(title.groupValues[2])
                }
                title != null -> {
                    finishParagraph()
                    blocks += LegalDocumentBlock.Heading(plainText(title.groupValues[2]), title.groupValues[1].length)
                }
                line.isBlank() -> finishParagraph()
                line.startsWith("|") && line.contains("---") -> finishParagraph()
                line.startsWith("|") -> {
                    finishParagraph()
                    val text = line.trim().trim('|').split('|').joinToString("：") { it.trim() }
                    blocks += LegalDocumentBlock.Paragraph(plainText(text))
                }
                unorderedItem != null -> {
                    finishParagraph()
                    blocks += LegalDocumentBlock.ListItem(plainText(unorderedItem.groupValues[1]), "•")
                }
                orderedItem != null -> {
                    finishParagraph()
                    blocks += LegalDocumentBlock.ListItem(plainText(orderedItem.groupValues[2]), "${orderedItem.groupValues[1]}.")
                }
                else -> paragraphLines += plainText(line)
            }
        }
        finishSection()
        require(sections.isNotEmpty()) { "离线文档正文为空" }
        return LegalDocument(type, sections)
    }

    private fun plainText(line: String): String {
        val text = inlineCodePattern.replace(line.replace("**", "")) { it.groupValues[1] }
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
