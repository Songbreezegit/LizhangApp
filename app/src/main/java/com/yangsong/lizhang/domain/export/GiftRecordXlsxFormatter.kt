package com.yangsong.lizhang.domain.export

import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 生成不依赖桌面 Office 库的标准 XLSX 工作簿。 */
object GiftRecordXlsxFormatter {
    private val headers = listOf("联系人", "金额（元）", "往来方向", "事件类型", "事件日期", "备注", "创建时间")

    fun format(records: List<GiftRecordWithContact>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeXml("[Content_Types].xml", contentTypes)
            zip.writeXml("_rels/.rels", rootRelationships)
            zip.writeXml("xl/workbook.xml", workbook)
            zip.writeXml("xl/_rels/workbook.xml.rels", workbookRelationships)
            zip.writeXml("xl/styles.xml", styles)
            zip.writeXml("xl/worksheets/sheet1.xml", worksheet(records))
        }
        return output.toByteArray()
    }

    private fun worksheet(records: List<GiftRecordWithContact>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<dimension ref=\"A1:G${records.size + 1}\"/>")
        append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<cols><col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"14\" customWidth=\"1\"/><col min=\"3\" max=\"5\" width=\"13\" customWidth=\"1\"/><col min=\"6\" max=\"6\" width=\"28\" customWidth=\"1\"/><col min=\"7\" max=\"7\" width=\"21\" customWidth=\"1\"/></cols>")
        append("<sheetData>")
        append("<row r=\"1\">")
        headers.forEachIndexed { index, value -> append(inlineCell(column(index), 1, value, style = 1)) }
        append("</row>")
        records.forEachIndexed { index, item ->
            val row = index + 2
            val record = item.record
            append("<row r=\"$row\">")
            append(inlineCell("A", row, item.contactName))
            append(numberCell("B", row, BigDecimal.valueOf(record.amountInCents, 2).toPlainString(), style = 2))
            append(inlineCell("C", row, record.direction.label()))
            append(inlineCell("D", row, record.eventType.label()))
            append(inlineCell("E", row, DateFormatter.format(record.eventDate)))
            append(inlineCell("F", row, record.notes.orEmpty()))
            append(inlineCell("G", row, DateFormatter.format(record.createdTime, "yyyy-MM-dd HH:mm:ss")))
            append("</row>")
        }
        append("</sheetData>")
        append("<autoFilter ref=\"A1:G${records.size + 1}\"/>")
        append("</worksheet>")
    }

    private fun inlineCell(column: String, row: Int, value: String, style: Int = 0): String =
        "<c r=\"$column$row\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${value.xmlEscape()}</t></is></c>"

    private fun numberCell(column: String, row: Int, value: String, style: Int): String =
        "<c r=\"$column$row\" s=\"$style\"><v>$value</v></c>"

    private fun column(index: Int): String = ('A'.code + index).toChar().toString()

    private fun String.xmlEscape(): String = filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun GiftDirection.label() = if (this == GiftDirection.RECEIVED) "收到" else "送出"

    private fun EventType.label() = when (this) {
        EventType.WEDDING -> "婚礼"
        EventType.FULL_MONTH -> "满月"
        EventType.BIRTHDAY -> "生日"
        EventType.HOUSEWARMING -> "乔迁"
        EventType.FESTIVAL -> "节日"
        EventType.OTHER -> "其他"
    }

    private fun ZipOutputStream.writeXml(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private const val rootRelationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="礼金记录" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private const val workbookRelationships = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private const val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFF6374"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
