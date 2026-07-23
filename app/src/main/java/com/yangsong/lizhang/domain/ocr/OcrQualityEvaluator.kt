package com.yangsong.lizhang.domain.ocr

import java.time.LocalDate

/** 以多项版面及业务信号评估结果，避免只依赖单一置信度阈值。 */
object OcrQualityEvaluator {
    private val amountCandidate = Regex("[¥￥]?\\s*\\d{1,7}(?:[.,]\\d{1,2})?\\s*元?")
    private val nameCandidate = Regex("[\\p{IsHan}·]{2,12}")

    fun evaluate(lines: List<OcrTextLine>, date: LocalDate = LocalDate.now()): OcrQualityReport {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        val parsed = OcrLedgerParser.parse(lines, date)
        val names = nonBlank.count { nameCandidate.containsMatchIn(it.text) }
        val amounts = nonBlank.count { amountCandidate.containsMatchIn(it.text) }
        val paired = parsed.size
        val denominator = maxOf(names, amounts, 1)
        val pairingRatio = paired.toFloat() / denominator
        val confidences = nonBlank.mapNotNull(OcrTextLine::confidence)
        val lowRatio = if (confidences.isEmpty()) 1f else confidences.count { it < 0.65f }.toFloat() / confidences.size
        val blankRatio = if (lines.isEmpty()) 1f else (lines.size - nonBlank.size).toFloat() / lines.size
        val abnormal = parsed.count { it.amountInCents !in 1..100_000_000_00L }
        val reasons = buildList {
            if (nonBlank.isEmpty()) add("未识别到有效文字")
            if (parsed.isEmpty()) add("未解析出礼金记录")
            if (names > 0 && amounts == 0) add("识别到姓名但缺少金额")
            if (amounts > 0 && names == 0) add("识别到金额但缺少姓名")
            if (pairingRatio < 0.5f) add("姓名与金额配对率偏低")
            if (lowRatio > 0.45f) add("低置信度文字过多")
            if (blankRatio > 0.5f) add("空白识别结果过多")
            if (abnormal > 0) add("存在异常金额")
            if (nonBlank.size > 8 && parsed.size * 4 < nonBlank.size) add("大量文字无法组成记录")
        }
        return OcrQualityReport(
            acceptable = reasons.isEmpty(),
            validLineCount = nonBlank.size,
            parsedRecordCount = parsed.size,
            nameCandidateCount = names,
            amountCandidateCount = amounts,
            pairingRatio = pairingRatio,
            lowConfidenceRatio = lowRatio,
            blankRatio = blankRatio,
            abnormalAmountCount = abnormal,
            reasons = reasons,
        )
    }
}
