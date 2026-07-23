package com.yangsong.lizhang.data.ocr

import com.yangsong.lizhang.data.remote.paddle.PaddleOcrJobClient
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import java.io.File

interface PaddleRecognitionSource {
    suspend fun recognizeFile(
        filePath: String,
        onStage: (OcrProcessingStage) -> Unit = {},
    ): OcrRecognitionResult
}

class PaddleOcrRecognitionRepository(
    private val jobClient: PaddleOcrJobClient,
    private val model: String,
    private val engine: OcrEngine,
) : OcrRecognitionRepository, PaddleRecognitionSource {
    override suspend fun recognize(imageUri: String) =
        recognizeFile(requireNotNull(android.net.Uri.parse(imageUri).path), {}).lines

    override suspend fun recognizeFile(
        filePath: String,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult = OcrRecognitionResult(
        lines = jobClient.recognize(File(filePath), model, onStage),
        engine = engine,
    )
}
