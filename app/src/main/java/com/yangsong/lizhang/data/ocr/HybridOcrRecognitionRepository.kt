package com.yangsong.lizhang.data.ocr

import com.yangsong.lizhang.data.image.OcrImagePreprocessor
import com.yangsong.lizhang.data.remote.paddle.PaddleCloudException
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrQualityEvaluator
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import kotlinx.coroutines.CancellationException

class HybridOcrRecognitionRepository(
    private val preprocessor: OcrImagePreprocessor,
    private val primary: PaddleRecognitionSource?,
    private val vlFallback: PaddleRecognitionSource?,
    private val offline: OcrRecognitionRepository,
    private val cloudConfigured: Boolean,
) : OcrRecognitionRepository {
    override suspend fun recognize(imageUri: String) =
        recognizeDetailed(imageUri, allowCloud = true).lines

    override suspend fun recognizeDetailed(
        imageUri: String,
        allowCloud: Boolean,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult {
        onStage(OcrProcessingStage.PREPROCESSING)
        val processed = preprocessor.preprocess(imageUri)
        return try {
            if (!allowCloud) {
                offline(processed.processedUri, OcrFallbackReason.CLOUD_DECLINED, processed.warnings, onStage)
            } else if (!cloudConfigured || primary == null || vlFallback == null) {
                offline(processed.processedUri, OcrFallbackReason.TOKEN_MISSING, processed.warnings, onStage)
            } else {
                recognizeCloud(processed.processedUri, processed.warnings, onStage)
            }
        } finally {
            preprocessor.clean(processed)
        }
    }

    private suspend fun recognizeCloud(
        fileUri: String,
        preprocessWarnings: List<String>,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult {
        val filePath = requireNotNull(java.net.URI(fileUri).path)
        val primaryResult = try {
            requireNotNull(primary).recognizeFile(filePath, onStage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PaddleCloudException) {
            if (error.reason in setOf(OcrFallbackReason.JOB_FAILED, OcrFallbackReason.MALFORMED_RESPONSE)) {
                return tryVlOrOffline(fileUri, filePath, emptyList(), preprocessWarnings, error.reason, onStage)
            }
            return offline(fileUri, error.reason, preprocessWarnings, onStage)
        }
        val report = OcrQualityEvaluator.evaluate(primaryResult.lines)
        if (report.acceptable) {
            onStage(OcrProcessingStage.COMPLETED)
            return primaryResult.copy(warnings = preprocessWarnings)
        }
        return tryVlOrOffline(
            fileUri = fileUri,
            filePath = filePath,
            primaryLines = primaryResult.lines,
            preprocessWarnings = preprocessWarnings + report.reasons,
            cloudFailureReason = OcrFallbackReason.CLOUD_RESULTS_LOW_QUALITY,
            onStage = onStage,
        )
    }

    private suspend fun tryVlOrOffline(
        fileUri: String,
        filePath: String,
        primaryLines: List<com.yangsong.lizhang.domain.ocr.OcrTextLine>,
        preprocessWarnings: List<String>,
        cloudFailureReason: OcrFallbackReason,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult = try {
        val vlResult = requireNotNull(vlFallback).recognizeFile(filePath, onStage)
        val primaryQuality = OcrQualityEvaluator.evaluate(primaryLines)
        val vlQuality = OcrQualityEvaluator.evaluate(vlResult.lines)
        val useVl = vlQuality.parsedRecordCount > primaryQuality.parsedRecordCount ||
            (vlQuality.parsedRecordCount == primaryQuality.parsedRecordCount &&
                vlQuality.pairingRatio > primaryQuality.pairingRatio)
        onStage(OcrProcessingStage.COMPLETED)
        if (useVl) {
            vlResult.copy(
                alternativeLines = primaryLines,
                fallbackReason = cloudFailureReason,
                warnings = preprocessWarnings + vlQuality.reasons,
            )
        } else {
            OcrRecognitionResult(
                lines = primaryLines,
                engine = OcrEngine.PP_OCR_V6,
                alternativeLines = vlResult.lines,
                fallbackReason = cloudFailureReason,
                warnings = preprocessWarnings + primaryQuality.reasons,
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: PaddleCloudException) {
        offline(fileUri, error.reason, preprocessWarnings, onStage)
    }

    private suspend fun offline(
        fileUri: String,
        reason: OcrFallbackReason,
        warnings: List<String>,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult {
        onStage(OcrProcessingStage.OFFLINE_RECOGNIZING)
        val lines = offline.recognize(fileUri)
        onStage(OcrProcessingStage.COMPLETED)
        return OcrRecognitionResult(
            lines = lines,
            engine = OcrEngine.ML_KIT,
            fallbackReason = reason,
            warnings = warnings,
        )
    }
}
