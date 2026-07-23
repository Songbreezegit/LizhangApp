package com.yangsong.lizhang.domain.ocr

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLedgerParserTest {
    @Test
    fun `同一文字行可提取姓名金额和完整日期`() {
        val result = OcrLedgerParser.parse(
            listOf(line("王阿姨 500元 2026-07-18", confidence = 0.95f)),
            LocalDate.of(2026, 7, 21),
        ).single()

        assertEquals("王阿姨", result.name)
        assertEquals(50_000, result.amountInCents)
        assertEquals(LocalDate.of(2026, 7, 18), result.date)
        assertFalse(result.lowConfidence)
    }

    @Test
    fun `同一版面行的多个单元格会按横向位置合并`() {
        val result = OcrLedgerParser.parse(
            listOf(
                line("800", left = 300),
                line("李叔叔", left = 20),
                line("7月20日", left = 500),
            ),
            LocalDate.of(2026, 7, 21),
        ).single()

        assertEquals("李叔叔", result.name)
        assertEquals(80_000, result.amountInCents)
        assertEquals(LocalDate.of(2026, 7, 20), result.date)
    }

    @Test
    fun `缺少日期时使用拍摄当天并标记重点核对`() {
        val fallback = LocalDate.of(2026, 7, 21)
        val result = OcrLedgerParser.parse(listOf(line("张三 200")), fallback).single()

        assertEquals(fallback, result.date)
        assertTrue(result.lowConfidence)
    }

    @Test
    fun `标题和无金额行不会生成记录`() {
        val result = OcrLedgerParser.parse(
            listOf(line("姓名 金额 日期"), line("只有姓名")),
            LocalDate.of(2026, 7, 21),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `千分位金额与姓名间隔列可正确解析`() {
        val result = OcrLedgerParser.parse(
            listOf(
                OcrTextLine("赵·小兰", 0.9f, 20, 20, 100, 50),
                OcrTextLine("￥1,200.50", 0.9f, 220, 20, 340, 50),
                OcrTextLine("2026年7月21日", 0.9f, 380, 20, 520, 50),
            ),
            LocalDate.of(2026, 7, 21),
        ).single()
        assertEquals("赵·小兰", result.name)
        assertEquals(120_050, result.amountInCents)
    }

    @Test
    fun `姓名金额纵向分列时可按列恢复记录`() {
        val result = OcrLedgerParser.parse(
            listOf(
                OcrTextLine("孙阿姨", 0.9f, 100, 20, 180, 50),
                OcrTextLine("贺礼", 0.9f, 110, 80, 170, 110),
                OcrTextLine("600元", 0.9f, 105, 140, 180, 170),
            ),
            LocalDate.of(2026, 7, 21),
        ).single()
        assertEquals("孙阿姨", result.name)
        assertEquals(60_000, result.amountInCents)
        assertTrue(result.lowConfidence)
    }

    private fun line(text: String, left: Int = 0, confidence: Float? = 0.9f) = OcrTextLine(
        text = text,
        confidence = confidence,
        left = left,
        top = 100,
        right = left + 100,
        bottom = 130,
    )
}
