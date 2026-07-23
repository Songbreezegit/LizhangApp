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
    private val amountPattern = Regex("[¥￥]?\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d{1,7}(?:[.,]\\d{1,2})?)\\s*元?")
    private val namePattern = Regex("[\\p{IsHan}A-Za-z·]{2,20}")
    private val ignoredNames = setOf("姓名", "金额", "日期", "礼金", "合计", "总计", "小计", "序号", "备注", "来宾", "签名")
    private const val MAX_REASONABLE_AMOUNT_CENTS = 100_000_000L

    fun parse(lines: List<OcrTextLine>, fallbackDate: LocalDate = LocalDate.now()): List<OcrImportDraft> {
        val rowDrafts = groupRows(lines).mapNotNull { parseGroup(it, fallbackDate, sortByHorizontal = true) }
        val columnDrafts = if (rowDrafts.isEmpty() || rowDrafts.size * 3 < lines.size) {
            groupColumns(lines).mapNotNull { parseGroup(it, fallbackDate, sortByHorizontal = false) }
        } else emptyList()
        return (rowDrafts + columnDrafts).distinctBy { "${it.name}|${it.amountInCents}|${it.date}" }
    }

    private fun parseGroup(
        lines: List<OcrTextLine>,
        fallbackDate: LocalDate,
        sortByHorizontal: Boolean,
    ): OcrImportDraft? {
        val ordered = if (sortByHorizontal) lines.sortedBy(OcrTextLine::left) else lines.sortedBy(OcrTextLine::top)
        val text = ordered.joinToString(" ") { it.text.trim() }
        if (ignoredNames.any { text.trim().startsWith(it) } && amountPattern.find(text) == null) return null
        val dateMatch = fullDatePattern.find(text) ?: shortDatePattern.find(text)
        val parsedDate = dateMatch?.let { parseDate(it, fallbackDate.year) }
        val date = parsedDate ?: fallbackDate
        val withoutDate = dateMatch?.let { text.removeRange(it.range) } ?: text
        val amountMatch = amountPattern.findAll(withoutDate)
            .filterNot { it.groupValues[1].length == 4 && it.groupValues[1].startsWith("20") }
            .lastOrNull() ?: return null
        val amountInCents = runCatching {
            BigDecimal(normalizeAmount(amountMatch.groupValues[1]))
                .setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        val nameSource = withoutDate.removeRange(amountMatch.range)
            .replace(Regex("[：:|,，;；\\d_\\-]+"), " ")
            .replace(Regex("(贺礼|收礼|送礼|现金|红包|转账)"), " ")
        val name = namePattern.findAll(nameSource).map { it.value.trim() }
            .firstOrNull {
                it !in ignoredNames && ignoredNames.none(it::contains) &&
                    it.any(Char::isLetter)
            } ?: return null
        val confidence = lines.mapNotNull(OcrTextLine::confidence).minOrNull()
        return OcrImportDraft(
            name = name,
            amountInCents = amountInCents,
            date = date,
            lowConfidence = confidence == null || confidence < 0.72f || parsedDate == null ||
                amountInCents > MAX_REASONABLE_AMOUNT_CENTS,
        )
    }

    private fun normalizeAmount(raw: String): String {
        val value = raw.trim()
        if (value.matches(Regex("\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?"))) return value.replace(",", "")
        return value.replace(',', '.')
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

    private fun groupColumns(lines: List<OcrTextLine>): List<List<OcrTextLine>> {
        val columns = mutableListOf<MutableList<OcrTextLine>>()
        lines.filter { it.text.isNotBlank() }
            .sortedBy { (it.left + it.right) / 2 }
            .forEach { line ->
                val center = (line.left + line.right) / 2
                val closest = columns.minByOrNull { column ->
                    kotlin.math.abs(center - column.map { (it.left + it.right) / 2 }.average())
                }
                val tolerance = maxOf(24, (line.right - line.left) / 2)
                val closestCenter = closest?.map { (it.left + it.right) / 2 }?.average()
                if (closest != null && closestCenter != null && kotlin.math.abs(center - closestCenter) <= tolerance) {
                    closest += line
                } else {
                    columns += mutableListOf(line)
                }
            }
        return columns
    }
}
