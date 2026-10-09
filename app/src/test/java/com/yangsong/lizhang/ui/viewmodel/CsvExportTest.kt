package com.yangsong.lizhang.ui.viewmodel
import com.yangsong.lizhang.fixtures.chineseExportLabels

import com.yangsong.lizhang.domain.export.GiftRecordCsvFormatter
import com.yangsong.lizhang.domain.export.GiftRecordXlsxFormatter
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.model.AppThemeMode
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.BackupDocument
import com.yangsong.lizhang.domain.repository.BackupRepository
import com.yangsong.lizhang.domain.repository.BackupSummary
import com.yangsong.lizhang.domain.repository.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.util.TimeZone
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExportTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `主题选择立即保存并反映到设置状态`() = runTest(dispatcher) {
        val themes = FakeThemeRepository()
        val viewModel = SettingsViewModel(
            CsvGiftRepository(emptyList()),
            FakeBackupRepository(),
            themes,
        )

        viewModel.setThemeMode(AppThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(AppThemeMode.DARK, themes.themeMode.value)
        assertEquals(AppThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `CSV 使用 BOM 中文表头并正确转义特殊字符`() {
        val csv = GiftRecordCsvFormatter.format(chineseExportLabels,
            listOf(sampleItem(contactName = "王,阿姨", notes = "祝福\"满满\"\n第二行")),
        )

        assertTrue(csv.startsWith("\uFEFF联系人,金额（元）,往来方向,事件类型"))
        assertTrue(csv.contains("\"王,阿姨\",123.45,收到,婚礼"))
        assertTrue(csv.contains("\"祝福\"\"满满\"\"\n第二行\""))
    }

    @Test
    fun `CSV 中和姓名自定义事件和备注中的公式前缀及全角变体`() {
        listOf("=1+2", "+1+2", "-1+2", "@SUM(1)", "＝1＋2", "＋1", "－1", "＠SUM(1)").forEach { text ->
            val item = sampleItem(contactName = text, notes = text).withCustomEvent(text)
            val csv = GiftRecordCsvFormatter.format(chineseExportLabels, listOf(item))
            val row = csv.parseCsvRows()[1]

            assertEquals("姓名前缀：$text", "\t$text", row[0])
            assertEquals("事件前缀：$text", "\t$text", row[3])
            assertEquals("备注前缀：$text", "\t$text", row[5])
            assertTrue("危险字段必须将制表符保留在双引号内", csv.contains("\"\t$text\""))
            assertEquals(7, row.size)
            assertEquals("123.45", row[1])
        }
    }

    @Test
    fun `CSV 前置空白控制符和不可见格式字符不能绕过公式中和`() {
        listOf(" ", "\u00A0", "\u3000", "\t", "\r", "\n", "\u0000", "\uFEFF", "\u200B", "\u202E", " \t\r\n").forEach { prefix ->
            val text = "${prefix}=1+2"
            val item = sampleItem(contactName = text, notes = text).withCustomEvent(text)
            val row = GiftRecordCsvFormatter.format(chineseExportLabels, listOf(item)).parseCsvRows()[1]

            assertEquals("\t$text", row[0])
            // 自定义事件名称沿用原有去除首尾空白的展示规则。
            assertEquals("\t${text.trim()}", row[3])
            assertEquals("\t$text", row[5])
            assertEquals(7, row.size)
        }
    }

    @Test
    fun `CSV 前置控制符即使不跟公式也作为文本中和`() {
        listOf("\t", "\r", "\n", "\u0000", "\uFEFF", "\u200B", "\u202E").forEach { prefix ->
            val text = "${prefix}说明"
            val row = GiftRecordCsvFormatter.format(
                chineseExportLabels, listOf(sampleItem(contactName = text, notes = text)),
            ).parseCsvRows()[1]

            assertEquals("\t$text", row[0])
            assertEquals("\t$text", row[5])
        }
    }

    @Test
    fun `CSV 中和文本标签但金额列包括负值保持数值格式`() {
        val labels = chineseExportLabels.copy(
            headers = chineseExportLabels.headers.toMutableList().apply { this[0] = "=1+2" },
            directions = chineseExportLabels.directions + (GiftDirection.RECEIVED to "+1"),
            events = chineseExportLabels.events + (EventType.WEDDING to "-1"),
        )
        val item = sampleItem().let { it.copy(record = it.record.copy(amountInCents = -12_345)) }
        val rows = GiftRecordCsvFormatter.format(labels, listOf(item)).parseCsvRows()

        assertEquals("\t=1+2", rows[0][0])
        assertEquals("-123.45", rows[1][1])
        assertEquals("\t+1", rows[1][2])
        assertEquals("\t-1", rows[1][3])
    }

    @Test
    fun `CSV 普通中文金额日期和原有空白保持原样`() {
        val previousTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val text = " 普通中文=1+2 '说明 "
            val item = sampleItem(contactName = text, notes = text).let {
                it.copy(record = it.record.copy(eventDate = 0, createdTime = 0))
            }
            val csv = GiftRecordCsvFormatter.format(chineseExportLabels, listOf(item))

            assertEquals(
                "\uFEFF联系人,金额（元）,往来方向,事件类型,事件日期,备注,创建时间\n" +
                    "$text,123.45,收到,婚礼,1970-01-01,$text,1970-01-01 00:00:00\n",
                csv,
            )
            assertEquals(2, csv.parseCsvRows().size)
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }

    @Test
    fun `CSV 引号逗号和换行不能将公式拆入新增单元格`() {
        listOf("=1+2\",@SUM(1)\r\n-1", "普通\",=1+2\n+1", "普通;=1+2").forEach { text ->
            val item = sampleItem(contactName = text, notes = text).withCustomEvent(text)
            val csv = GiftRecordCsvFormatter.format(chineseExportLabels, listOf(item))
            val rows = csv.parseCsvRows()
            val exportedText = if (text.startsWith("=")) "\t$text" else text

            assertEquals(2, rows.size)
            assertEquals(7, rows[0].size)
            assertEquals(7, rows[1].size)
            assertEquals(exportedText, rows[1][0])
            assertEquals(exportedText, rows[1][3])
            assertEquals(exportedText, rows[1][5])
            if (';' in text) assertTrue(csv.contains("\"$text\""))
        }
    }

    @Test
    fun `XLSX 公式外观文本仍使用显式文本类型且不添加CSV制表符`() {
        val text = "=1+2"
        val item = sampleItem(contactName = text, notes = text).withCustomEvent(text)
        val sheet = GiftRecordXlsxFormatter.format(chineseExportLabels, listOf(item))
            .unzipXmlEntries().getValue("xl/worksheets/sheet1.xml")

        listOf("A2", "D2", "F2").forEach { coordinate ->
            assertTrue(sheet.contains("<c r=\"$coordinate\" t=\"inlineStr\" s=\"0\"><is><t xml:space=\"preserve\">$text</t></is></c>"))
        }
        assertFalse(sheet.contains("<f>"))
        assertFalse(sheet.contains("\t$text"))
        assertTrue(sheet.contains("<v>123.45</v>"))
    }

    @Test
    fun `设置页生成带时间戳文件名的 CSV 待保存文档`() = runTest(dispatcher) {
        val now = 1_752_830_645_000
        val viewModel = SettingsViewModel(
            CsvGiftRepository(listOf(sampleItem())),
            backupRepository = FakeBackupRepository(),
            now = { now },
        )

        viewModel.prepareCsvExport(chineseExportLabels)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreparingCsv)
        val document = viewModel.uiState.value.pendingExport
        assertEquals("Lizhang_20250718_172405.csv", document?.fileName)
        assertEquals("text/csv", document?.mimeType)
        assertEquals(ExportFormat.CSV, document?.format)
        assertTrue(document?.bytes?.toString(Charsets.UTF_8).orEmpty().contains("张同学,123.45,收到,婚礼"))
    }

    @Test
    fun `导出文案可本地化且金额和自定义事件保持原值`() {
        val labels = chineseExportLabels.copy(
            headers = listOf("Contact", "Amount (CNY)", "Direction", "Occasion", "Date", "Notes", "Created At"),
            sheetName = "Gift Records",
            directions = mapOf(GiftDirection.RECEIVED to "Received", GiftDirection.GIVEN to "Given"),
            events = EventType.entries.associateWith { "Occasion" },
        )
        val item = sampleItem().let { it.copy(record = it.record.copy(eventType = EventType.OTHER, customEventName = "测试事件")) }
        val csv = GiftRecordCsvFormatter.format(labels, listOf(item))
        assertTrue(csv.startsWith("\uFEFFContact,Amount (CNY),Direction"))
        assertTrue(csv.contains("123.45,Received,测试事件"))
        val entries = GiftRecordXlsxFormatter.format(labels, listOf(item)).unzipXmlEntries()
        assertTrue(entries.getValue("xl/workbook.xml").contains("Gift Records"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("Received"))
        assertTrue(entries.getValue("xl/worksheets/sheet1.xml").contains("测试事件"))
    }

    @Test
    fun `XLSX 包含标准工作簿结构与已转义业务数据`() {
        val bytes = GiftRecordXlsxFormatter.format(chineseExportLabels,
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

        viewModel.prepareExcelExport(chineseExportLabels)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreparingExcel)
        val document = viewModel.uiState.value.pendingExport
        assertEquals("Lizhang_20250718_172405.xlsx", document?.fileName)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", document?.mimeType)
        assertEquals(ExportFormat.EXCEL, document?.format)
        assertEquals('P'.code.toByte(), document?.bytes?.get(0))
    }

    @Test
    fun `没有记录时不启动文件保存器`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(CsvGiftRepository(emptyList()), FakeBackupRepository())

        viewModel.prepareCsvExport(chineseExportLabels)
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
        assertEquals("Lizhang_backup_20250718_172405.lizhangbackup", document?.fileName)
        assertEquals(ExportFormat.BACKUP, document?.format)
        assertArrayEquals(backupRepository.backupBytes, document?.bytes)
    }

    @Test
    fun `设置页生成加密备份时使用独立文件名并传递密码`() = runTest(dispatcher) {
        val now = 1_752_830_645_000
        val backupRepository = FakeBackupRepository()
        val viewModel = SettingsViewModel(
            CsvGiftRepository(emptyList()),
            backupRepository = backupRepository,
            now = { now },
            backgroundDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        )

        viewModel.prepareBackupExport("安全密码123")
        advanceUntilIdle()

        assertEquals("安全密码123", backupRepository.createdPassword)
        assertEquals(
            "Lizhang_encrypted_backup_20250718_172405.lizhangbackup",
            viewModel.uiState.value.pendingExport?.fileName,
        )
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

    @Test
    fun `加密备份需要正确密码后才能进入恢复确认`() = runTest(dispatcher) {
        val backupRepository = FakeBackupRepository()
        val viewModel = SettingsViewModel(
            CsvGiftRepository(emptyList()),
            backupRepository,
            backgroundDispatcher = dispatcher,
            computeDispatcher = dispatcher,
        )

        viewModel.inspectBackup(backupRepository.encryptedBytes)
        assertArrayEquals(
            backupRepository.encryptedBytes,
            viewModel.uiState.value.encryptedBackupAwaitingPassword,
        )
        assertNull(viewModel.uiState.value.pendingRestore)

        viewModel.unlockEncryptedBackup("错误密码")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBackupPasswordInvalid)
        assertNull(viewModel.uiState.value.pendingRestore)

        viewModel.unlockEncryptedBackup("安全密码123")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isBackupPasswordInvalid)
        assertEquals(backupRepository.summary, viewModel.uiState.value.pendingRestore?.summary)

        viewModel.confirmRestore()
        advanceUntilIdle()
        assertEquals("安全密码123", backupRepository.restoredPassword)
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

    private fun GiftRecordWithContact.withCustomEvent(text: String): GiftRecordWithContact =
        copy(record = record.copy(eventType = EventType.OTHER, customEventName = text))
}

/** 独立读取字段结构，验证引号内的换行和分隔符不会产生额外记录或列。 */
private fun String.parseCsvRows(): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = if (startsWith("\uFEFF")) 1 else 0
    while (index < length) {
        val character = this[index]
        when {
            character == '"' && quoted && getOrNull(index + 1) == '"' -> {
                cell.append('"')
                index++
            }
            character == '"' -> quoted = !quoted
            !quoted && character == ',' -> {
                cells.add(cell.toString())
                cell.clear()
            }
            !quoted && character == '\n' -> {
                cells.add(cell.toString())
                rows.add(cells.toList())
                cells.clear()
                cell.clear()
            }
            else -> cell.append(character)
        }
        index++
    }
    check(!quoted) { "CSV 存在未闭合引号" }
    if (cell.isNotEmpty() || cells.isNotEmpty()) {
        cells.add(cell.toString())
        rows.add(cells.toList())
    }
    return rows
}

private class FakeThemeRepository : ThemeRepository {
    private val mutableThemeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    override val themeMode: StateFlow<AppThemeMode> = mutableThemeMode

    override fun setThemeMode(mode: AppThemeMode) {
        mutableThemeMode.value = mode
    }
}

private class FakeBackupRepository : BackupRepository {
    val backupBytes = byteArrayOf(1, 2, 3)
    val encryptedBytes = byteArrayOf(9, 2, 3)
    val summary = BackupSummary(createdTime = 1, contactCount = 2, giftRecordCount = 3)
    var restoredBytes: ByteArray? = null
    var createdPassword: String? = null
    var restoredPassword: String? = null

    override suspend fun createBackup(password: String?): BackupDocument {
        createdPassword = password
        return BackupDocument(
            bytes = if (password == null) backupBytes else encryptedBytes,
            summary = summary,
        )
    }

    override fun requiresPassword(bytes: ByteArray): Boolean = bytes.contentEquals(encryptedBytes)

    override fun inspectBackup(bytes: ByteArray, password: String?): BackupSummary {
        if (requiresPassword(bytes) && password != "安全密码123") {
            throw com.yangsong.lizhang.domain.backup.InvalidBackupPasswordException()
        }
        return summary
    }

    override suspend fun restoreBackup(bytes: ByteArray, password: String?): BackupSummary {
        restoredBytes = bytes
        restoredPassword = password
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
