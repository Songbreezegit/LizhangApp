package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.export.GiftRecordXlsxFormatter
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.BackupDocument
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.BackupSummary
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

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
        val viewModel = SettingsViewModel(
            CsvGiftRepository(listOf(sampleItem())),
            backupRepository = FakeBackupRepository(),
            now = { now },
        )

        viewModel.prepareCsvExport()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreparingCsv)
        val document = viewModel.uiState.value.pendingExport
        assertEquals("礼账_20250718_172405.csv", document?.fileName)
        assertEquals("text/csv", document?.mimeType)
        assertEquals(ExportFormat.CSV, document?.format)
        assertTrue(document?.bytes?.toString(Charsets.UTF_8).orEmpty().contains("张同学,123.45,收到,婚礼"))
    }

    @Test
    fun `XLSX 包含标准工作簿结构与已转义业务数据`() {
        val bytes = GiftRecordXlsxFormatter.format(
            listOf(sampleItem(contactName = "王&阿姨", notes = "祝福<满满>")),
        )
        val entries = bytes.unzipXmlEntries()
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")

        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
        assertTrue(entries.keys.containsAll(REQUIRED_XLSX_ENTRIES))
        assertTrue(sheet.contains("联系人"))
        assertTrue(sheet.contains("王&amp;阿姨"))
        assertTrue(sheet.contains("祝福&lt;满满&gt;"))
        assertTrue(sheet.contains("<v>123.45</v>"))
        assertTrue(sheet.contains("state=\"frozen\""))
        assertTrue(sheet.contains("<autoFilter ref=\"A1:G2\"/>"))
    }

    @Test
    fun `设置页生成带时间戳文件名的 Excel 待保存文档`() = runTest(dispatcher) {
        val now = 1_752_830_645_000
        val viewModel = SettingsViewModel(
            CsvGiftRepository(listOf(sampleItem())),
            backupRepository = FakeBackupRepository(),
            now = { now },
        )

        viewModel.prepareExcelExport()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreparingExcel)
        val document = viewModel.uiState.value.pendingExport
        assertEquals("礼账_20250718_172405.xlsx", document?.fileName)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", document?.mimeType)
        assertEquals(ExportFormat.EXCEL, document?.format)
        assertEquals('P'.code.toByte(), document?.bytes?.get(0))
    }

    @Test
    fun `没有记录时不启动文件保存器`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(CsvGiftRepository(emptyList()), FakeBackupRepository())

        viewModel.prepareCsvExport()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingExport)
        assertEquals(SettingsMessage.EXPORT_EMPTY, viewModel.uiState.value.message)
    }

    @Test
    fun `设置页生成带时间戳文件名的礼账备份`() = runTest(dispatcher) {
        val now = 1_752_830_645_000
        val backupRepository = FakeBackupRepository()
        val viewModel = SettingsViewModel(
            CsvGiftRepository(emptyList()),
            backupRepository = backupRepository,
            now = { now },
            backgroundDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        )

        viewModel.prepareBackupExport()
        advanceUntilIdle()

        val document = viewModel.uiState.value.pendingExport
        assertFalse(viewModel.uiState.value.isPreparingBackup)
        assertEquals("礼账备份_20250718_172405.lizhangbackup", document?.fileName)
        assertEquals(ExportFormat.BACKUP, document?.format)
        assertArrayEquals(backupRepository.backupBytes, document?.bytes)
    }

    @Test
    fun `校验备份后必须确认才执行恢复`() = runTest(dispatcher) {
        val backupRepository = FakeBackupRepository()
        val viewModel = SettingsViewModel(
            CsvGiftRepository(emptyList()),
            backupRepository,
            backgroundDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        )

        viewModel.inspectBackup(backupRepository.backupBytes)
        advanceUntilIdle()
        assertEquals(backupRepository.summary, viewModel.uiState.value.pendingRestore?.summary)
        assertNull(backupRepository.restoredBytes)

        viewModel.confirmRestore()
        advanceUntilIdle()

        assertArrayEquals(backupRepository.backupBytes, backupRepository.restoredBytes)
        assertNull(viewModel.uiState.value.pendingRestore)
        assertEquals(SettingsMessage.BACKUP_RESTORE_SUCCESS, viewModel.uiState.value.message)
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

private class FakeBackupRepository : BackupRepository {
    val backupBytes = byteArrayOf(1, 2, 3)
    val summary = BackupSummary(createdTime = 1, contactCount = 2, giftRecordCount = 3)
    var restoredBytes: ByteArray? = null

    override suspend fun createBackup() = BackupDocument(
        bytes = backupBytes,
        summary = summary,
    )

    override fun inspectBackup(bytes: ByteArray) = summary

    override suspend fun restoreBackup(bytes: ByteArray): BackupSummary {
        restoredBytes = bytes
        return summary
    }
}

private val REQUIRED_XLSX_ENTRIES = setOf(
    "[Content_Types].xml",
    "_rels/.rels",
    "xl/workbook.xml",
    "xl/_rels/workbook.xml.rels",
    "xl/styles.xml",
    "xl/worksheets/sheet1.xml",
)

private fun ByteArray.unzipXmlEntries(): Map<String, String> = buildMap {
    ZipInputStream(ByteArrayInputStream(this@unzipXmlEntries)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
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
