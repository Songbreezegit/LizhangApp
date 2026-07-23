package com.yangsong.lizhang.data.di

import android.content.Context
import androidx.room.Room
import com.yangsong.lizhang.core.common.DatabaseConstants
import com.yangsong.lizhang.data.local.LiZhangDatabase
import com.yangsong.lizhang.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yangsong.lizhang.data.image.OpenCvOcrImagePreprocessor
import com.yangsong.lizhang.data.ocr.HybridOcrRecognitionRepository
import com.yangsong.lizhang.data.ocr.MlKitOcrRecognitionRepository
import com.yangsong.lizhang.data.ocr.PaddleOcrRecognitionRepository
import com.yangsong.lizhang.data.ocr.RoomOcrImportRepository
import com.yangsong.lizhang.data.remote.paddle.PaddleOcrApi
import com.yangsong.lizhang.data.remote.paddle.PaddleOcrJobClient
import com.yangsong.lizhang.data.remote.paddle.PaddleOcrResultMapper
import com.yangsong.lizhang.data.reminder.AndroidReminderRepository
import com.yangsong.lizhang.data.reminder.ReminderCoordinator
import com.yangsong.lizhang.data.repository.RoomBackupRepository
import com.yangsong.lizhang.data.repository.RoomContactRepository
import com.yangsong.lizhang.data.repository.RoomGiftRecordRepository
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.OcrImportRepository
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import com.yangsong.lizhang.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.yangsong.lizhang.domain.ocr.OcrEngine

/** 应用级依赖组合根，避免在界面层直接创建数据库或仓库。 */
class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database: LiZhangDatabase = Room.databaseBuilder(
        context.applicationContext,
        LiZhangDatabase::class.java,
        DatabaseConstants.NAME,
    ).build()

    val contactRepository: ContactRepository = RoomContactRepository(database.contactDao())
    val giftRecordRepository: GiftRecordRepository = RoomGiftRecordRepository(database.giftRecordDao())
    val backupRepository: BackupRepository = RoomBackupRepository(database)
    val reminderRepository: ReminderRepository = AndroidReminderRepository(context.applicationContext)
    private val mlKitOcrRepository = MlKitOcrRecognitionRepository(context.applicationContext)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val ocrHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val paddleApi = Retrofit.Builder()
        .baseUrl("https://paddleocr.aistudio-app.com/")
        .client(ocrHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PaddleOcrApi::class.java)
    private val paddleClient = PaddleOcrJobClient(
        paddleApi,
        ocrHttpClient,
        PaddleOcrResultMapper(moshi),
        BuildConfig.AISTUDIO_ACCESS_TOKEN,
    )
    private val primaryPaddleRepository = if (BuildConfig.AISTUDIO_CLOUD_OCR_ENABLED) {
        PaddleOcrRecognitionRepository(
            paddleClient,
            PaddleOcrJobClient.MODEL_PP_OCR_V6,
            OcrEngine.PP_OCR_V6,
        )
    } else null
    private val vlPaddleRepository = if (BuildConfig.AISTUDIO_CLOUD_OCR_ENABLED) {
        PaddleOcrRecognitionRepository(
            paddleClient,
            PaddleOcrJobClient.MODEL_PADDLE_OCR_VL_1_6,
            OcrEngine.PADDLE_OCR_VL_1_6,
        )
    } else null
    val ocrRecognitionRepository: OcrRecognitionRepository = HybridOcrRecognitionRepository(
        preprocessor = OpenCvOcrImagePreprocessor(context.applicationContext),
        primary = primaryPaddleRepository,
        vlFallback = vlPaddleRepository,
        offline = mlKitOcrRepository,
        cloudConfigured = BuildConfig.AISTUDIO_CLOUD_OCR_ENABLED &&
            BuildConfig.AISTUDIO_ACCESS_TOKEN.isNotBlank(),
    )
    val ocrImportRepository: OcrImportRepository = RoomOcrImportRepository(database)
    private val reminderCoordinator = ReminderCoordinator(
        giftRecordRepository,
        reminderRepository,
        applicationScope,
    )

    fun startReminderCoordination() = reminderCoordinator.start()
    fun refreshReminderSchedules() = reminderCoordinator.refresh()
}
