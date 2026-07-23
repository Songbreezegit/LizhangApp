package com.yangsong.lizhang.domain.ocr

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrQualityEvaluatorTest {
    @Test
    fun `完整姓名金额记录质量合格`() {
        val report = OcrQualityEvaluator.evaluate(
            listOf(OcrTextLine("张三 200元 2026-07-20", 0.95f, 0, 0, 200, 30)),
            LocalDate.of(2026, 7, 20),
        )
        assertTrue(report.acceptable)
    }

    @Test
    fun `只有姓名或大量低置信度时触发兜底`() {
        val report = OcrQualityEvaluator.evaluate(
            listOf(
                OcrTextLine("张三", 0.2f, 0, 0, 100, 30),
                OcrTextLine("李四", 0.3f, 0, 40, 100, 70),
            ),
        )
        assertFalse(report.acceptable)
        assertTrue(report.reasons.isNotEmpty())
    }
}
