package com.yangsong.lizhang.domain.ocr

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class OcrTextLine(
    val text: String,
    val confidence: Float?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class OcrImportDraft(
    val name: String,
    val amountInCents: Long,
    val date: LocalDate,
    val lowConfidence: Boolean,
)

/** 将文字识别结果按版面行组合，再提取姓名、金额和日期。 */
object OcrLedgerParser {
    private val fullDatePattern = Regex("(20\\d{2})[年./\\-](\\d{1,2})[月./\\-](\\d{1,2})日?")
    private val shortDatePattern = Regex("(?<!\\d)(\\d{1,2})[月./\\-](\\d{1,2})日?")
    private val amountPattern = Regex("[¥￥]?\\s*(\\d{1,7}(?:[.,]\\d{1,2})?)\\s*元?")
    private val namePattern = Regex("[\\p{L}·]{1,20}")
    private val ignoredNames = setOf("姓名", "金额", "日期", "礼金", "合计", "总计", "序号", "备注")

    fun parse(lines: List<OcrTextLine>, fallbackDate: LocalDate = LocalDate.now()): List<OcrImportDraft> =
        groupRows(lines).mapNotNull { parseRow(it, fallbackDate) }

    private fun parseRow(lines: List<OcrTextLine>, fallbackDate: LocalDate): OcrImportDraft? {
        val text = lines.sortedBy(OcrTextLine::left).joinToString(" ") { it.text.trim() }
        val dateMatch = fullDatePattern.find(text) ?: shortDatePattern.find(text)
        val parsedDate = dateMatch?.let { parseDate(it, fallbackDate.year) }
        val date = parsedDate ?: fallbackDate
        val withoutDate = dateMatch?.let { text.removeRange(it.range) } ?: text
        val amountMatch = amountPattern.findAll(withoutDate)
            .filterNot { it.groupValues[1].length == 4 && it.groupValues[1].startsWith("20") }
            .lastOrNull() ?: return null
        val amountInCents = runCatching {
            BigDecimal(amountMatch.groupValues[1].replace(',', '.'))
                .setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        val nameSource = withoutDate.removeRange(amountMatch.range)
            .replace(Regex("[：:|,，;；\\d_\\-]+"), " ")
        val name = namePattern.findAll(nameSource).map { it.value.trim() }
            .firstOrNull { it !in ignoredNames && it.any(Char::isLetter) } ?: return null
        val confidence = lines.mapNotNull(OcrTextLine::confidence).minOrNull()
        return OcrImportDraft(
            name = name,
            amountInCents = amountInCents,
            date = date,
            lowConfidence = confidence == null || confidence < 0.72f || parsedDate == null,
        )
    }

    private fun parseDate(match: MatchResult, fallbackYear: Int): LocalDate? = runCatching {
        if (match.groupValues.size == 4) {
            LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
        } else {
            LocalDate.of(fallbackYear, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }.getOrNull()

    private fun groupRows(lines: List<OcrTextLine>): List<List<OcrTextLine>> {
        val rows = mutableListOf<MutableList<OcrTextLine>>()
        lines.filter { it.text.isNotBlank() }.sortedBy { it.top }.forEach { line ->
            val center = (line.top + line.bottom) / 2
            val existing = rows.lastOrNull()
            val existingCenter = existing?.map { (it.top + it.bottom) / 2 }?.average()
            val tolerance = maxOf(18, line.bottom - line.top)
            if (existing != null && existingCenter != null && kotlin.math.abs(center - existingCenter) <= tolerance) {
                existing += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows
    }
}
