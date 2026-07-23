package com.yangsong.lizhang.data.ocr

import com.yangsong.lizhang.data.image.OcrImagePreprocessor
import com.yangsong.lizhang.data.image.OcrPreprocessResult
import com.yangsong.lizhang.data.remote.paddle.PaddleCloudException
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.ocr.OcrTextLine
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridOcrRecognitionRepositoryTest {
    @Test
    fun `主模型高质量时不调用VL`() = runTest {
        val primary = FakePaddleSource(goodLines())
        val vl = FakePaddleSource(goodLines("李四 300元 2026-07-20"), engine = OcrEngine.PADDLE_OCR_VL_1_6)
        val result = repository(primary, vl).recognizeDetailed("content://image", true)
        assertEquals(OcrEngine.PP_OCR_V6, result.engine)
        assertEquals(0, vl.calls)
    }

    @Test
    fun `主模型低质量时调用VL并保留候选`() = runTest {
        val primary = FakePaddleSource(listOf(line("模糊文字", 0.1f)))
        val vl = FakePaddleSource(goodLines(), engine = OcrEngine.PADDLE_OCR_VL_1_6)
        val result = repository(primary, vl).recognizeDetailed("content://image", true)
        assertEquals(OcrEngine.PADDLE_OCR_VL_1_6, result.engine)
        assertTrue(result.alternativeLines.isNotEmpty())
        assertEquals(OcrFallbackReason.CLOUD_RESULTS_LOW_QUALITY, result.fallbackReason)
    }

    @Test
    fun `云端失败自动降级MLKit并进入完成状态`() = runTest {
        val primary = FakePaddleSource(error = PaddleCloudException(OcrFallbackReason.RATE_LIMITED, "限流"))
        val stages = mutableListOf<OcrProcessingStage>()
        val result = repository(primary, FakePaddleSource(goodLines()))
            .recognizeDetailed("content://image", true, stages::add)
        assertEquals(OcrEngine.ML_KIT, result.engine)
        assertEquals(OcrFallbackReason.RATE_LIMITED, result.fallbackReason)
        assertTrue(OcrProcessingStage.OFFLINE_RECOGNIZING in stages)
    }

    @Test
    fun `令牌未配置时直接使用MLKit`() = runTest {
        val result = repository(
            FakePaddleSource(goodLines()),
            FakePaddleSource(goodLines()),
            cloudConfigured = false,
        ).recognizeDetailed("content://image", true)
        assertEquals(OcrFallbackReason.TOKEN_MISSING, result.fallbackReason)
        assertEquals(OcrEngine.ML_KIT, result.engine)
    }

    private fun repository(
        primary: PaddleRecognitionSource,
        vl: PaddleRecognitionSource,
        cloudConfigured: Boolean = true,
    ) = HybridOcrRecognitionRepository(
        FakePreprocessor(),
        primary,
        vl,
        FakeOfflineRepository(),
        cloudConfigured,
    )

    private fun goodLines(text: String = "张三 200元 2026-07-20") = listOf(line(text, 0.95f))
    private fun line(text: String, confidence: Float?) = OcrTextLine(text, confidence, 0, 0, 200, 30)
}

private class FakePaddleSource(
    private val lines: List<OcrTextLine> = emptyList(),
    private val error: PaddleCloudException? = null,
    private val engine: OcrEngine = OcrEngine.PP_OCR_V6,
) : PaddleRecognitionSource {
    var calls = 0
    override suspend fun recognizeFile(
        filePath: String,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult {
        calls++
        error?.let { throw it }
        return OcrRecognitionResult(lines, engine)
    }
}

private class FakePreprocessor : OcrImagePreprocessor {
    override suspend fun preprocess(imageUri: String) = OcrPreprocessResult(
        imageUri, "file:///tmp/test.jpg", 100, 100, 100, 100, false, false, emptyList(),
    )
    override fun clean(result: OcrPreprocessResult) = Unit
}

private class FakeOfflineRepository : OcrRecognitionRepository {
    override suspend fun recognize(imageUri: String) =
        listOf(OcrTextLine("离线姓名 100元 2026-07-20", 0.5f, 0, 0, 200, 30))
}
