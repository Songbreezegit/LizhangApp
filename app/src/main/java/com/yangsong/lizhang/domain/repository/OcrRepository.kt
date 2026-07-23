package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.ocr.OcrImportDraft
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrTextLine

interface OcrRecognitionRepository {
    suspend fun recognize(imageUri: String): List<OcrTextLine>

    suspend fun recognizeDetailed(
        imageUri: String,
        allowCloud: Boolean,
        onStage: (OcrProcessingStage) -> Unit = {},
    ): OcrRecognitionResult = OcrRecognitionResult(
        lines = recognize(imageUri),
        engine = OcrEngine.ML_KIT,
    )
}

interface OcrImportRepository {
    suspend fun import(records: List<OcrImportDraft>): Int
}
