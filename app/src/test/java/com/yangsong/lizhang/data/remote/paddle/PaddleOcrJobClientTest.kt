package com.yangsong.lizhang.data.remote.paddle

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PaddleOcrJobClientTest {
    private lateinit var server: MockWebServer
    private lateinit var image: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        image = File.createTempFile("ocr-test", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        image.delete()
        server.shutdown()
    }

    @Test
    fun `任务按pending running done轮询并下载结果`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"pending"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"running"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"done","resultUrl":{"jsonUrl":"${server.url("/result")}"}}}"""))
        server.enqueue(json(ocrJsonl()))

        val lines = client(pollInterval = 1).recognize(image, "PP-OCRv6") {}

        assertEquals("张三", lines.first().text)
        assertEquals(5, server.requestCount)
        assertTrue(server.takeRequest().getHeader("Authorization")?.startsWith("bearer ") == true)
    }

    @Test
    fun `failed状态转为可降级错误`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"failed","errorMsg":"safe"}}"""))
        val error = runCatching { client().recognize(image, "PP-OCRv6") {} }.exceptionOrNull()
        assertEquals(OcrFallbackReason.JOB_FAILED, (error as PaddleCloudException).reason)
    }

    @Test
    fun `401与403不会泄露令牌并转为令牌错误`() = runBlocking {
        listOf(401, 403).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"code":401}"""))
            val error = runCatching { client().recognize(image, "PP-OCRv6") {} }.exceptionOrNull()
            assertEquals(OcrFallbackReason.TOKEN_INVALID, (error as PaddleCloudException).reason)
            assertTrue(error.message?.contains("test-token") == false)
        }
    }

    @Test
    fun `429转为限流错误且不重试`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val error = runCatching { client().recognize(image, "PP-OCRv6") {} }.exceptionOrNull()
        assertEquals(OcrFallbackReason.RATE_LIMITED, (error as PaddleCloudException).reason)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `403配额错误转为每日额度耗尽`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":12001}"""))
        val error = runCatching { client().recognize(image, "PP-OCRv6") {} }.exceptionOrNull()
        assertEquals(OcrFallbackReason.DAILY_QUOTA_EXHAUSTED, (error as PaddleCloudException).reason)
    }

    @Test
    fun `服务端错误有限重试后成功`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"done","resultUrl":{"jsonUrl":"${server.url("/result")}"}}}"""))
        server.enqueue(json(ocrJsonl()))
        assertEquals(2, client().recognize(image, "PP-OCRv6") {}.size)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `结果下载失败转为明确错误`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"done","resultUrl":{"jsonUrl":"${server.url("/result")}"}}}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        val error = runCatching { client().recognize(image, "PP-OCRv6") {} }.exceptionOrNull()
        assertEquals(OcrFallbackReason.JOB_FAILED, (error as PaddleCloudException).reason)
    }

    @Test
    fun `轮询超时会取消任务`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"pending"}}"""))
        val error = runCatching {
            client(pollInterval = 50, timeout = 10).recognize(image, "PP-OCRv6") {}
        }.exceptionOrNull()
        assertEquals(OcrFallbackReason.TIMEOUT, (error as PaddleCloudException).reason)
    }

    @Test
    fun `调用协程取消时停止后续轮询`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"jobId":"ocrjob-test"}}"""))
        server.enqueue(json("""{"code":0,"data":{"state":"pending"}}"""))
        val job = launch { client(pollInterval = 5_000).recognize(image, "PP-OCRv6") {} }
        delay(100)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun client(pollInterval: Long = 1, timeout: Long = 5_000): PaddleOcrJobClient {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val http = OkHttpClient()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(http)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PaddleOcrApi::class.java)
        return PaddleOcrJobClient(api, http, PaddleOcrResultMapper(moshi), "test-token", pollInterval, timeout)
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun ocrJsonl() =
        """{"result":{"ocrResults":[{"prunedResult":{"rec_texts":["张三","100元"],"rec_scores":[0.9,0.9],"rec_boxes":[[0,0,20,10],[30,0,60,10]]}}]}}"""
}
