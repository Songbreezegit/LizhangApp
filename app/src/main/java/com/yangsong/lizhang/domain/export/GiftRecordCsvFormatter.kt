package com.yangsong.lizhang.domain.export

import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.math.BigDecimal

/** 将礼金记录转换为可被 Excel 正确识别的 UTF-8 CSV。 */
object GiftRecordCsvFormatter {
    private const val UTF8_BOM = "\uFEFF"

    fun format(records: List<GiftRecordWithContact>): String = buildString {
        append(UTF8_BOM)
        appendLine("联系人,金额（元）,往来方向,事件类型,事件日期,备注,创建时间")
        records.forEach { item ->
            val record = item.record
            appendLine(
                listOf(
                    item.contactName,
                    BigDecimal.valueOf(record.amountInCents, 2).toPlainString(),
                    record.direction.csvLabel(),
                    record.eventExportLabel(),
                    DateFormatter.format(record.eventDate),
                    record.notes.orEmpty(),
                    DateFormatter.format(record.createdTime, "yyyy-MM-dd HH:mm:ss"),
                ).joinToString(",", transform = ::escape),
            )
        }
    }

    private fun escape(value: String): String {
        val requiresQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (requiresQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun GiftDirection.csvLabel() = when (this) {
        GiftDirection.RECEIVED -> "收到"
        GiftDirection.GIVEN -> "送出"
    }

}
