package com.yangsong.lizhang.domain.repository

import com.yangsong.lizhang.domain.ocr.OcrImportDraft
import com.yangsong.lizhang.domain.ocr.OcrTextLine

interface OcrRecognitionRepository {
    suspend fun recognize(imageUri: String): List<OcrTextLine>
}

interface OcrImportRepository {
    suspend fun import(records: List<OcrImportDraft>): Int
}
