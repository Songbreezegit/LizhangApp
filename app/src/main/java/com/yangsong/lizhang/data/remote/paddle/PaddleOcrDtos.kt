package com.yangsong.lizhang.data.remote.paddle

data class PaddleJobEnvelope(
    val traceId: String? = null,
    val code: Int = 0,
    val msg: String? = null,
    val data: PaddleJobData? = null,
)

data class PaddleJobData(
    val jobId: String? = null,
    val state: String? = null,
    val errorMsg: String? = null,
    val resultUrl: PaddleResultUrl? = null,
    val extractProgress: PaddleExtractProgress? = null,
)

data class PaddleResultUrl(
    val jsonUrl: String? = null,
    val markdownUrl: String? = null,
)

data class PaddleExtractProgress(
    val startTime: String? = null,
    val endTime: String? = null,
    val totalPages: String? = null,
    val extractedPages: String? = null,
)
