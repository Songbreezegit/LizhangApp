package com.yangsong.lizhang.data.remote.paddle

import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrTextLine
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class PaddleCloudException(
    val reason: OcrFallbackReason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class PaddleOcrJobClient(
    private val api: PaddleOcrApi,
    private val httpClient: OkHttpClient,
    private val resultMapper: PaddleOcrResultMapper,
    private val token: String,
    private val pollIntervalMillis: Long = 5_000,
    private val taskTimeoutMillis: Long = 120_000,
) {
    suspend fun recognize(
        file: File,
        model: String,
        onStage: (OcrProcessingStage) -> Unit,
    ): List<OcrTextLine> = try {
        withTimeout(taskTimeoutMillis) {
            onStage(OcrProcessingStage.UPLOADING)
            val jobId = createWithRetry(file, model)
            onStage(if (model == MODEL_PP_OCR_V6) OcrProcessingStage.PRIMARY_RECOGNIZING else OcrProcessingStage.VL_RECOGNIZING)
            downloadAndMap(poll(jobId, onStage))
        }
    } catch (error: TimeoutCancellationException) {
        throw PaddleCloudException(OcrFallbackReason.TIMEOUT, "云端识别超时", error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: PaddleCloudException) {
        throw error
    } catch (error: SocketTimeoutException) {
        throw PaddleCloudException(OcrFallbackReason.TIMEOUT, "云端识别超时", error)
    } catch (error: IOException) {
        throw PaddleCloudException(OcrFallbackReason.NETWORK_UNAVAILABLE, "网络不可用", error)
    } catch (error: Exception) {
        throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "云端响应结构异常", error)
    }

    private suspend fun createWithRetry(file: File, model: String): String {
        repeat(MAX_SERVER_ATTEMPTS) { attempt ->
            val response = api.createJob(
                authorization = "bearer $token",
                file = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType())),
                model = model.toRequestBody("text/plain".toMediaType()),
                optionalPayload = optionalPayload(model)?.toRequestBody("text/plain".toMediaType()),
            )
            if (response.code() in 500..599 && attempt + 1 < MAX_SERVER_ATTEMPTS) {
                delay(1_000L shl attempt)
            } else {
                return requireSuccess(response).data?.jobId
                    ?: throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "任务编号缺失")
            }
        }
        throw PaddleCloudException(OcrFallbackReason.SERVER_ERROR, "云端服务暂不可用")
    }

    private suspend fun poll(jobId: String, onStage: (OcrProcessingStage) -> Unit): String {
        while (true) {
            val response = api.getJob("bearer $token", jobId)
            val envelope = requireSuccess(response)
            when (envelope.data?.state) {
                "pending", "running" -> {
                    onStage(OcrProcessingStage.WAITING_RESULT)
                    delay(pollIntervalMillis)
                }
                "done" -> return envelope.data.resultUrl?.jsonUrl
                    ?: throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "结果地址缺失")
                "failed" -> throw PaddleCloudException(OcrFallbackReason.JOB_FAILED, "云端任务失败")
                else -> throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "未知任务状态")
            }
        }
    }

    private fun downloadAndMap(url: String): List<OcrTextLine> {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw statusException(response.code)
            return resultMapper.map(response.body.string()).takeIf(List<OcrTextLine>::isNotEmpty)
                ?: throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "结果中没有可用文字")
        }
    }

    private fun requireSuccess(response: Response<PaddleJobEnvelope>): PaddleJobEnvelope {
        val httpCode = response.code()
        val apiCode = response.body()?.code ?: parseApiCode(response.errorBody()?.string())
        if (httpCode !in 200..299) throw statusException(httpCode, apiCode)
        val envelope = response.body()
        val value = envelope ?: throw PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "响应为空")
        if (value.code != 0) {
            throw when (value.code) {
                12001 -> PaddleCloudException(OcrFallbackReason.DAILY_QUOTA_EXHAUSTED, "今日云端 OCR 额度已用尽")
                12002 -> PaddleCloudException(OcrFallbackReason.RATE_LIMITED, "云端请求频率过高")
                11003 -> PaddleCloudException(OcrFallbackReason.JOB_FAILED, "云端任务失败")
                else -> statusException(httpCode, value.code)
            }
        }
        return value
    }

    private fun parseApiCode(errorBody: String?): Int? =
        errorBody?.let { Regex("\"code\"\\s*:\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun statusException(httpCode: Int, apiCode: Int? = null) = when (httpCode) {
        400 -> PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "请求参数或图片格式不受支持")
        401 -> PaddleCloudException(OcrFallbackReason.TOKEN_INVALID, "AI Studio 令牌无效")
        403 -> if (apiCode == 12001) {
            PaddleCloudException(OcrFallbackReason.DAILY_QUOTA_EXHAUSTED, "今日云端 OCR 额度已用尽")
        } else PaddleCloudException(OcrFallbackReason.TOKEN_INVALID, "AI Studio 权限不足")
        404 -> PaddleCloudException(OcrFallbackReason.JOB_FAILED, "任务或结果不存在")
        413 -> PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "图片过大，请压缩或重新拍摄")
        429 -> PaddleCloudException(OcrFallbackReason.RATE_LIMITED, "云端请求频率过高")
        in 500..599 -> PaddleCloudException(OcrFallbackReason.SERVER_ERROR, "云端服务暂不可用")
        else -> PaddleCloudException(OcrFallbackReason.MALFORMED_RESPONSE, "云端请求失败")
    }

    private fun optionalPayload(model: String): String? = if (model == MODEL_PP_OCR_V6) {
        // PP-OCRv6 当前真实接口已验证省略可选参数可成功；避免沿用旧版字段造成 10008。
        null
    } else {
        """{"useDocOrientationClassify":false,"useDocUnwarping":false,"useChartRecognition":false}"""
    }

    companion object {
        const val MODEL_PP_OCR_V6 = "PP-OCRv6"
        const val MODEL_PADDLE_OCR_VL_1_6 = "PaddleOCR-VL-1.6"
        private const val MAX_SERVER_ATTEMPTS = 3
    }
}
