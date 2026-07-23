package com.yangsong.lizhang.data.image

import android.net.Uri

data class OcrPreprocessResult(
    val originalUri: String,
    val processedUri: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val processedWidth: Int,
    val processedHeight: Int,
    val perspectiveCorrected: Boolean,
    val usedFallback: Boolean,
    val warnings: List<String>,
)

interface OcrImagePreprocessor {
    suspend fun preprocess(imageUri: String): OcrPreprocessResult
    fun clean(result: OcrPreprocessResult)
}

internal fun OcrPreprocessResult.processedAndroidUri(): Uri = Uri.parse(processedUri)
