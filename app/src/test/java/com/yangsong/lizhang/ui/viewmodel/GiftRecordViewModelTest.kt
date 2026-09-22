package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GiftRecordViewModelTest {
    @Test fun `自定义事件新建编辑及标准事件清除`() = runTest(dispatcher) {
        val repository = FakeGiftRecordRepository(sampleRecord())
        val contacts = FakeContactRepository(sampleContact())
        val editor = GiftEditorViewModel(repository, contacts, initialContactId = sampleContact().id)
        editor.update { it.copy(amount = "100") }
        editor.setCustomEvent("  升学宴  ")
        assertEquals("升学宴", editor.uiState.value.customEventName)
        assertEquals(EventType.OTHER, editor.uiState.value.eventType)
        editor.save()
        advanceUntilIdle()
        assertEquals("升学宴", repository.createdRecord?.customEventName)
        val existing = sampleRecord().copy(eventType = EventType.OTHER, customEventName = "升学宴")
        val editRepository = FakeGiftRecordRepository(existing)
        val edit = GiftEditorViewModel(editRepository, contacts, existing.id)
        advanceUntilIdle()
        assertEquals("升学宴", edit.uiState.value.customEventName)
        edit.selectEvent(EventType.BIRTHDAY)
        assertNull(edit.uiState.value.customEventName)
        edit.setCustomEvent("开业")
        edit.selectEvent(EventType.OTHER)
        assertNull(edit.uiState.value.customEventName)
        edit.setCustomEvent("百日宴")
        edit.save()
        advanceUntilIdle()
        assertEquals("百日宴", editRepository.updatedRecord?.customEventName)
    }

    @Test fun `空白及超长自定义事件不改变选择`() = runTest(dispatcher) {
        val editor = GiftEditorViewModel(FakeGiftRecordRepository(sampleRecord()), FakeContactRepository(sampleContact()))
        editor.setCustomEvent("  ")
        editor.setCustomEvent("长".repeat(21))
        assertEquals(EventType.WEDDING, editor.uiState.value.eventType)
        assertNull(editor.uiState.value.customEventName)
        editor.setCustomEvent("毕业2026 ABC")
        assertEquals("毕业2026 ABC", editor.uiState.value.customEventName)
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `编辑页按记录编号恢复数据并保存更新`() = runTest(dispatcher) {
        val record = sampleRecord()
        val giftRepository = FakeGiftRecordRepository(record)
        val contactRepository = FakeContactRepository(sampleContact())
        val viewModel = GiftEditorViewModel(giftRepository, contactRepository, record.id)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("100", viewModel.uiState.value.amount)
        assertEquals(record.contactId, viewModel.uiState.value.contactId)

        viewModel.update { it.copy(amount = "288.88", notes = "已修改") }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(28_888L, giftRepository.updatedRecord?.amountInCents)
        assertEquals("已修改", giftRepository.updatedRecord?.notes)
        assertEquals(record.createdTime, giftRepository.updatedRecord?.createdTime)
    }

    @Test
    fun `详情页删除记录后发布完成状态`() = runTest(dispatcher) {
        val record = sampleRecord()
        val giftRepository = FakeGiftRecordRepository(record)
        val viewModel = GiftRecordDetailViewModel(record.id, giftRepository)
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        assertEquals(record.id, viewModel.uiState.value.item?.record?.id)

        viewModel.delete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleted)
        assertEquals(record.id, giftRepository.deletedRecord?.id)
        collection.cancel()
    }

    @Test
    fun `从联系人详情记一笔时自动预选当前联系人`() = runTest(dispatcher) {
        val contact = sampleContact()
        val viewModel = GiftEditorViewModel(
            FakeGiftRecordRepository(sampleRecord()),
            FakeContactRepository(contact),
            initialContactId = contact.id,
        )

        advanceUntilIdle()

        assertEquals(contact.id, viewModel.uiState.value.contactId)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `礼金日期晚于今天时阻止保存`() = runTest(dispatcher) {
        val now = 1_753_200_000_000
        val repository = FakeGiftRecordRepository(sampleRecord())
        val viewModel = GiftEditorViewModel(
            repository,
            FakeContactRepository(sampleContact()),
            initialContactId = 3,
            now = { now },
        )
        viewModel.update {
            it.copy(
                amount = "200",
                eventDate = now + 86_400_000,
            )
        }

        viewModel.save()
        advanceUntilIdle()

        assertEquals(GiftRecordValidationError.DATE_IN_FUTURE, viewModel.uiState.value.validationError)
        assertNull(repository.createdRecord)
    }

    @Test
    fun `连续点击在调度前和成功后都只创建一次`() = runTest(dispatcher) {
        val repository = FakeGiftRecordRepository(sampleRecord())
        val viewModel = GiftEditorViewModel(repository, FakeContactRepository(sampleContact()), initialContactId = 3)
        viewModel.update { it.copy(amount = "100", eventDate = 1_753_200_000_000) }
        viewModel.save()
        assertTrue(viewModel.uiState.value.isSaving)
        repeat(5) { viewModel.save() }
        advanceUntilIdle()
        assertEquals(1, repository.createCount)
        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(1, repository.createCount)
    }

    @Test
    fun `失败保留全部字段消费反馈后可再次保存`() = runTest(dispatcher) {
        val repository = FakeGiftRecordRepository(sampleRecord()).apply { failCreate = true }
        val viewModel = GiftEditorViewModel(repository, FakeContactRepository(sampleContact()), initialContactId = 3)
        advanceUntilIdle()
        viewModel.update { it.copy(amount = "288.88", eventDate = 1_753_200_000_000,
            direction = GiftDirection.GIVEN, eventType = EventType.BIRTHDAY, notes = "测试备注") }
        val before = viewModel.uiState.value
        viewModel.save()
        advanceUntilIdle()
        val failed = viewModel.uiState.value
        assertTrue(failed.operationFailed)
        assertFalse(failed.isSaving)
        assertFalse(failed.isSaved)
        assertEquals(before, failed.copy(operationFailed = false))
        viewModel.consumeOperationFailure()
        assertFalse(viewModel.uiState.value.operationFailed)
        repository.failCreate = false
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.operationFailed)
        assertEquals(2, repository.createCount)
        assertEquals("测试备注", repository.createdRecord?.notes)
    }

    @Test
    fun `保存等待期间不重复写入也不改变待保存内容`() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = FakeGiftRecordRepository(sampleRecord()).apply { beforeCreate = { gate.await() } }
        val viewModel = GiftEditorViewModel(repository, FakeContactRepository(sampleContact()), initialContactId = 3)
        viewModel.update { it.copy(amount = "100", eventDate = 1_753_200_000_000, notes = "保留内容") }
        viewModel.save()
        runCurrent()
        viewModel.save()
        viewModel.update { it.copy(amount = "200", notes = "误触修改") }
        assertEquals("100", viewModel.uiState.value.amount)
        assertEquals(1, repository.createCount)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(10000L, repository.createdRecord?.amountInCents)
        assertEquals("保留内容", repository.createdRecord?.notes)
    }
    private fun sampleContact() = Contact(id = 3, name = "测试联系人", phone = "13800000000")

    private fun sampleRecord() = GiftRecord(
        id = 7,
        contactId = 3,
        amountInCents = 10_000,
        eventType = EventType.WEDDING,
        eventDate = 1_753_200_000_000,
        direction = GiftDirection.RECEIVED,
        notes = "原始备注",
        createdTime = 1_700_000_000_000,
    )
}

private class FakeGiftRecordRepository(initial: GiftRecord) : GiftRecordRepository {
    private val record = MutableStateFlow<GiftRecord?>(initial)
    private val item = MutableStateFlow<GiftRecordWithContact?>(GiftRecordWithContact(initial, "测试联系人"))
    var createCount = 0
    var failCreate = false
    var beforeCreate: (suspend () -> Unit)? = null
    var updatedRecord: GiftRecord? = null
    var createdRecord: GiftRecord? = null
    var deletedRecord: GiftRecord? = null

    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> = flowOf(item.value?.let(::listOf).orEmpty())
    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flowOf(item.value?.let(::listOf).orEmpty())
    override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> =
        flowOf(item.value?.takeIf { it.record.direction == direction }?.let(::listOf).orEmpty())
    override fun observeRecord(recordId: Long): Flow<GiftRecord?> = record
    override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> = item
    override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> = flowOf(record.value?.let(::listOf).orEmpty())
    override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> = flowOf(item.value?.let(::listOf).orEmpty())
    override suspend fun create(record: GiftRecord): Long {
        createCount++
        beforeCreate?.invoke()
        if (failCreate) error("模拟保存失败")
        createdRecord = record
        return 1
    }
    override suspend fun update(record: GiftRecord) {
        updatedRecord = record
        this.record.value = record
        item.value = GiftRecordWithContact(record, "测试联系人")
    }
    override suspend fun delete(record: GiftRecord) {
        deletedRecord = record
        this.record.value = null
        item.value = null
    }
}

private class FakeContactRepository(contact: Contact) : ContactRepository {
    override suspend fun previewDelete(ids: Set<Long>): com.yangsong.lizhang.domain.model.ContactDeletePreview = error("此测试不执行批量删除")
    override suspend fun deleteContacts(ids: Set<Long>, confirmed: com.yangsong.lizhang.domain.model.ContactDeletePreview): com.yangsong.lizhang.domain.model.ContactBulkDeleteOutcome = error("此测试不执行批量删除")
    override suspend fun importDeviceContacts(selections: List<com.yangsong.lizhang.domain.model.ContactImportSelection>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    override suspend fun createAll(contacts: List<Contact>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    private val contacts = MutableStateFlow(listOf(contact))
    override fun observeContacts(query: String): Flow<List<Contact>> = contacts
    override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> = flowOf(emptyList())
    override fun observeContact(contactId: Long): Flow<Contact?> = flowOf(contacts.value.firstOrNull { it.id == contactId })
    override suspend fun create(contact: Contact): Long = 99
    override suspend fun update(contact: Contact) = Unit
    override suspend fun delete(contact: Contact) = Unit
}
