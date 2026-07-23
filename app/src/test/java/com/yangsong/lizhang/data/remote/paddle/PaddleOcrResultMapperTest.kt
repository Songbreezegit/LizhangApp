package com.yangsong.lizhang.data.remote.paddle

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddleOcrResultMapperTest {
    private val mapper = PaddleOcrResultMapper(Moshi.Builder().build())

    @Test
    fun `PP OCR结果映射文字置信度与坐标`() {
        val jsonl = """{"result":{"ocrResults":[{"prunedResult":{"rec_texts":["张三","100元"],"rec_scores":[0.96,0.91],"rec_boxes":[[10,20,80,50],[100,20,170,50]]}}]}}"""
        val lines = mapper.map(jsonl)
        assertEquals(listOf("张三", "100元"), lines.map { it.text })
        assertEquals(10, lines.first().left)
        assertEquals(0.96f, lines.first().confidence)
    }

    @Test
    fun `VL结果从Markdown表格映射为领域文字行`() {
        val jsonl = """{"result":{"layoutParsingResults":[{"markdown":{"text":"| 姓名 | 金额 |\n|---|---|\n| 李四 | 200元 |","images":{}}}]}}"""
        assertTrue(mapper.map(jsonl).any { it.text.contains("李四 200元") })
    }

    @Test
    fun `缺失业务字段时返回空列表而不是崩溃`() {
        assertTrue(mapper.map("""{"result":{"unknown":[]}}""").isEmpty())
    }
}
