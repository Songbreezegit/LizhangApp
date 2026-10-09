package com.yangsong.lizhang.domain.export

import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.math.BigDecimal

/** 将礼金记录转换为可被 Excel 正确识别的 UTF-8 CSV。 */
object GiftRecordCsvFormatter {
    private const val UTF8_BOM = "\uFEFF"

    fun format(labels: GiftExportLabels, records: List<GiftRecordWithContact>): String = buildString {
        append(UTF8_BOM)
        appendLine(labels.headers.joinToString(",", transform = ::escapeText))
        records.forEach { item ->
            val record = item.record
            appendLine(
                listOf(
                    escapeText(item.contactName),
                    escape(BigDecimal.valueOf(record.amountInCents, 2).toPlainString()),
                    escapeText(labels.directions.getValue(record.direction)),
                    escapeText(record.eventExportLabel(labels)),
                    escape(DateFormatter.format(record.eventDate, "yyyy-MM-dd")),
                    escapeText(record.notes.orEmpty()),
                    escape(DateFormatter.format(record.createdTime, "yyyy-MM-dd HH:mm:ss")),
                ).joinToString(","),
            )
        }
    }

    /**
     * 面向表格软件查看的 CSV：危险文本以引号内的制表符中和，防止作为公式执行。
     * 跳过可能被导入器忽略的前置空白、控制符和格式字符后再判断公式前缀。
     * 制表符会保留在导出文本中；此保护仅用于文本列，不改变金额、日期或源数据。
     * 不同导入器和另存流程行为可能不同，需要精确保留文本类型时应使用 XLSX。
     */
    private fun escapeText(value: String): String {
        val contentIndex = value.indexOfFirst { !it.isIgnoredPrefix() }
        val prefixLength = if (contentIndex < 0) value.length else contentIndex
        val hasLeadingControl = value.take(prefixLength).any { it.isISOControl() || it.isFormatCharacter() }
        val startsFormula = value.getOrNull(contentIndex)?.let { it in "=+-@＝＋－＠" } == true
        return if (startsFormula || hasLeadingControl) escape("\t$value", forceQuotes = true) else escape(value)
    }

    private fun Char.isIgnoredPrefix(): Boolean = isWhitespace() || isISOControl() || isFormatCharacter()

    private fun Char.isFormatCharacter(): Boolean = Character.getType(this) == Character.FORMAT.toInt()

    private fun escape(value: String, forceQuotes: Boolean = false): String {
        // 分号也包裹，避免使用分号分隔的表格导入器将用户文本拆成新的公式单元格。
        val requiresQuotes = forceQuotes || value.any { it == ',' || it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (requiresQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }

}
