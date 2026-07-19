package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExportTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `CSV 使用 BOM 中文表头并正确转义特殊字符`() {
        val csv = GiftRecordCsvFormatter.format(
            listOf(sampleItem(contactName = "王,阿姨", notes = "祝福\"满满\"\n第二行")),
        )

        assertTrue(csv.startsWith("\uFEFF联系人,金额（元）,往来方向,事件类型"))
        assertTrue(csv.contains("\"王,阿姨\",123.45,收到,婚礼"))
        assertTrue(csv.contains("\"祝福\"\"满满\"\"\n第二行\""))
    }

    @Test
    fun `设置页生成带时间戳文件名的 CSV 待保存文档`() = runTest(dispatcher) {
        val now = 1_752_830_645_000
        val viewModel = SettingsViewModel(CsvGiftRepository(listOf(sampleItem())), now = { now })

        viewModel.prepareCsvExport()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreparingCsv)
        assertEquals("礼账_20250718_172405.csv", viewModel.uiState.value.pendingCsv?.fileName)
        assertTrue(viewModel.uiState.value.pendingCsv?.content.orEmpty().contains("张同学,123.45,收到,婚礼"))
    }

    @Test
    fun `没有记录时不启动文件保存器`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(CsvGiftRepository(emptyList()))

        viewModel.prepareCsvExport()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingCsv)
        assertEquals(SettingsMessage.CSV_EMPTY, viewModel.uiState.value.message)
    }

    private fun sampleItem(contactName: String = "张同学", notes: String? = "同学婚礼") = GiftRecordWithContact(
        record = GiftRecord(
            id = 1,
            contactId = 2,
            amountInCents = 12_345,
            eventType = EventType.WEDDING,
            eventDate = 1_752_787_200_000,
            direction = GiftDirection.RECEIVED,
            notes = notes,
            createdTime = 1_752_787_200_000,
        ),
        contactName = contactName,
    )
}

private class CsvGiftRepository(private val records: List<GiftRecordWithContact>) : GiftRecordRepository {
    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> = flowOf(records.take(limit))
    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flowOf(records)
    override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> = flowOf(records.filter { it.record.direction == direction })
    override fun observeRecord(recordId: Long): Flow<GiftRecord?> = flowOf(records.firstOrNull { it.record.id == recordId }?.record)
    override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> = flowOf(records.firstOrNull { it.record.id == recordId })
    override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> = flowOf(records.map { it.record }.filter { it.contactId == contactId })
    override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> = flowOf(records)
    override suspend fun create(record: GiftRecord): Long = record.id
    override suspend fun update(record: GiftRecord) = Unit
    override suspend fun delete(record: GiftRecord) = Unit
}
