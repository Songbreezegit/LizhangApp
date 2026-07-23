package com.yangsong.lizhang.domain.ocr

enum class OcrEngine {
    PP_OCR_V6,
    PADDLE_OCR_VL_1_6,
    ML_KIT,
}

enum class OcrProcessingStage {
    PREPROCESSING,
    UPLOADING,
    PRIMARY_RECOGNIZING,
    WAITING_RESULT,
    VL_RECOGNIZING,
    OFFLINE_RECOGNIZING,
    COMPLETED,
}

enum class OcrFallbackReason {
    TOKEN_MISSING,
    CLOUD_DECLINED,
    NETWORK_UNAVAILABLE,
    TOKEN_INVALID,
    DAILY_QUOTA_EXHAUSTED,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    JOB_FAILED,
    MALFORMED_RESPONSE,
    CLOUD_RESULTS_LOW_QUALITY,
}

data class OcrRecognitionResult(
    val lines: List<OcrTextLine>,
    val engine: OcrEngine,
    val alternativeLines: List<OcrTextLine> = emptyList(),
    val fallbackReason: OcrFallbackReason? = null,
    val warnings: List<String> = emptyList(),
)

data class OcrQualityReport(
    val acceptable: Boolean,
    val validLineCount: Int,
    val parsedRecordCount: Int,
    val nameCandidateCount: Int,
    val amountCandidateCount: Int,
    val pairingRatio: Float,
    val lowConfidenceRatio: Float,
    val blankRatio: Float,
    val abnormalAmountCount: Int,
    val reasons: List<String>,
)
