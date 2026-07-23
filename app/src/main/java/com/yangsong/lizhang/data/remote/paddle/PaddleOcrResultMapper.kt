package com.yangsong.lizhang.data.remote.paddle

import com.squareup.moshi.Moshi
import com.yangsong.lizhang.domain.ocr.OcrTextLine

/** 将 PP-OCR 与 VL 的 JSONL 响应收敛为领域层文字框。 */
class PaddleOcrResultMapper(moshi: Moshi) {
    private val anyAdapter = moshi.adapter(Any::class.java)

    fun map(jsonl: String): List<OcrTextLine> {
        val documents = jsonl.lineSequence().filter(String::isNotBlank).mapNotNull {
            runCatching { anyAdapter.fromJson(it) }.getOrNull()
        }.toList()
        return documents.flatMap(::findOcrLines).ifEmpty { documents.flatMap(::findMarkdownLines) }
    }

    private fun findOcrLines(node: Any?): List<OcrTextLine> {
        if (node is Map<*, *>) {
            val texts = node["rec_texts"] as? List<*>
            if (texts != null) {
                val scores = node["rec_scores"] as? List<*>
                val boxes = (node["rec_boxes"] ?: node["rec_polys"]) as? List<*>
                return texts.mapIndexedNotNull { index, raw ->
                    val text = raw as? String ?: return@mapIndexedNotNull null
                    val bounds = bounds(boxes?.getOrNull(index))
                    OcrTextLine(
                        text = text,
                        confidence = (scores?.getOrNull(index) as? Number)?.toFloat(),
                        left = bounds[0],
                        top = bounds[1],
                        right = bounds[2],
                        bottom = bounds[3],
                    )
                }
            }
            return node.values.flatMap(::findOcrLines)
        }
        return if (node is List<*>) node.flatMap(::findOcrLines) else emptyList()
    }

    private fun bounds(raw: Any?): IntArray {
        val values = mutableListOf<Double>()
        fun collect(value: Any?) {
            when (value) {
                is Number -> values += value.toDouble()
                is List<*> -> value.forEach(::collect)
            }
        }
        collect(raw)
        if (values.size < 4) return intArrayOf(0, 0, 1, 1)
        if (values.size == 4) return intArrayOf(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt())
        val xs = values.filterIndexed { index, _ -> index % 2 == 0 }
        val ys = values.filterIndexed { index, _ -> index % 2 == 1 }
        return intArrayOf(xs.min().toInt(), ys.min().toInt(), xs.max().toInt(), ys.max().toInt())
    }

    private fun findMarkdownLines(node: Any?): List<OcrTextLine> {
        if (node is Map<*, *>) {
            val text = (node["markdown"] as? Map<*, *>)?.get("text") as? String
            if (text != null) return markdownToLines(text)
            return node.values.flatMap(::findMarkdownLines)
        }
        return if (node is List<*>) node.flatMap(::findMarkdownLines) else emptyList()
    }

    private fun markdownToLines(markdown: String): List<OcrTextLine> =
        markdown.lineSequence()
            .map { it.trim().trim('|').replace(Regex("\\s*\\|\\s*"), " ") }
            .filter { it.isNotBlank() && !it.matches(Regex("[-: ]+")) }
            .mapIndexed { index, text -> OcrTextLine(text, null, 0, index * 40, 1000, index * 40 + 32) }
            .toList()
}
