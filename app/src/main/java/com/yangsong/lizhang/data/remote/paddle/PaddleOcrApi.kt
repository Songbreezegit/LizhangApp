package com.yangsong.lizhang.data.remote.paddle

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PaddleOcrApi {
    @Multipart
    @POST("api/v2/ocr/jobs")
    suspend fun createJob(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("optionalPayload") optionalPayload: RequestBody?,
    ): Response<PaddleJobEnvelope>

    @GET("api/v2/ocr/jobs/{jobId}")
    suspend fun getJob(
        @Header("Authorization") authorization: String,
        @Path("jobId") jobId: String,
    ): Response<PaddleJobEnvelope>
}
