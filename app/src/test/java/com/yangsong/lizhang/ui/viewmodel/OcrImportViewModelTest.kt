package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.ocr.OcrImportDraft
import com.yangsong.lizhang.domain.ocr.OcrEngine
import com.yangsong.lizhang.domain.ocr.OcrFallbackReason
import com.yangsong.lizhang.domain.ocr.OcrProcessingStage
import com.yangsong.lizhang.domain.ocr.OcrRecognitionResult
import com.yangsong.lizhang.domain.ocr.OcrTextLine
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.OcrImportRepository
import com.yangsong.lizhang.domain.repository.OcrRecognitionRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `识别结果进入待确认列表并标记已有重复记录`() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 7, 18)
        val existing = GiftRecordWithContact(
            GiftRecord(
                id = 1,
                contactId = 1,
                amountInCents = 50_000,
                eventType = EventType.OTHER,
                eventDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                direction = GiftDirection.RECEIVED,
            ),
            "王阿姨",
        )
        val viewModel = OcrImportViewModel(
            recognitionRepository = TestRecognitionRepository("王阿姨 500元 2026-07-18"),
            importRepository = TestOcrImportRepository(),
            giftRecordRepository = TestOcrGiftRepository(listOf(existing)),
        )

        viewModel.recognize("content://test/image")
        advanceUntilIdle()

        assertEquals(OcrStage.PENDING, viewModel.uiState.value.stage)
        assertEquals("王阿姨", viewModel.uiState.value.records.single().name)
        assertTrue(viewModel.uiState.value.records.single().possibleDuplicate)
    }

    @Test
    fun `确认有效记录后调用事务导入并展示数量`() = runTest(dispatcher) {
        val importer = TestOcrImportRepository()
        val viewModel = OcrImportViewModel(
            recognitionRepository = TestRecognitionRepository("李叔叔 800元 2026-07-20"),
            importRepository = importer,
            giftRecordRepository = TestOcrGiftRepository(emptyList()),
        )

        viewModel.recognize("content://test/image")
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(1, importer.imported.size)
        assertEquals(OcrStage.SUCCESS, viewModel.uiState.value.stage)
        assertEquals(1, viewModel.uiState.value.importedCount)
    }

    @Test
    fun `云端降级后仍进入校对列表并保留降级原因`() = runTest(dispatcher) {
        val viewModel = OcrImportViewModel(
            recognitionRepository = StagedRecognitionRepository(),
            importRepository = TestOcrImportRepository(),
            giftRecordRepository = TestOcrGiftRepository(emptyList()),
        )

        viewModel.recognize("content://test/image", allowCloud = true)
        advanceUntilIdle()

        assertEquals(OcrStage.PENDING, viewModel.uiState.value.stage)
        assertEquals(OcrEngine.ML_KIT, viewModel.uiState.value.engine)
        assertEquals(OcrFallbackReason.RATE_LIMITED, viewModel.uiState.value.fallbackReason)
        assertEquals(1, viewModel.uiState.value.records.size)
    }
}

private class TestRecognitionRepository(private val text: String) : OcrRecognitionRepository {
    override suspend fun recognize(imageUri: String) = listOf(OcrTextLine(text, 0.95f, 0, 0, 400, 40))
}

private class StagedRecognitionRepository : OcrRecognitionRepository {
    override suspend fun recognize(imageUri: String) = emptyList<OcrTextLine>()
    override suspend fun recognizeDetailed(
        imageUri: String,
        allowCloud: Boolean,
        onStage: (OcrProcessingStage) -> Unit,
    ): OcrRecognitionResult {
        onStage(OcrProcessingStage.UPLOADING)
        onStage(OcrProcessingStage.OFFLINE_RECOGNIZING)
        return OcrRecognitionResult(
            lines = listOf(OcrTextLine("周阿姨 300元 2026-07-24", 0.8f, 0, 0, 300, 40)),
            engine = OcrEngine.ML_KIT,
            fallbackReason = OcrFallbackReason.RATE_LIMITED,
        )
    }
}

private class TestOcrImportRepository : OcrImportRepository {
    var imported: List<OcrImportDraft> = emptyList()
    override suspend fun import(records: List<OcrImportDraft>): Int {
        imported = records
        return records.size
    }
}

private class TestOcrGiftRepository(private val records: List<GiftRecordWithContact>) : GiftRecordRepository {
    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> = flowOf(records.take(limit))
    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flowOf(records)
    override fun observeByDirection(direction: GiftDirection) = flowOf(records.filter { it.record.direction == direction })
    override fun observeRecord(recordId: Long) = flowOf(records.firstOrNull { it.record.id == recordId }?.record)
    override fun observeRecordWithContact(recordId: Long) = flowOf(records.firstOrNull { it.record.id == recordId })
    override fun observeByContact(contactId: Long) = flowOf(records.map { it.record }.filter { it.contactId == contactId })
    override fun observeSearch(query: String) = flowOf(records.filter { it.contactName.contains(query) })
    override suspend fun create(record: GiftRecord) = record.id
    override suspend fun update(record: GiftRecord) = Unit
    override suspend fun delete(record: GiftRecord) = Unit
}
