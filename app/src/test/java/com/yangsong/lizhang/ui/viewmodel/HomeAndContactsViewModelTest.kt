package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecord
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.ContactRepository
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import com.yangsong.lizhang.domain.repository.ReminderRepository
import com.yangsong.lizhang.domain.reminder.ReminderSettings
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAndContactsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `首页只汇总本年收送金额并限制最近记录数量`() = runTest(dispatcher) {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val currentYearDate = dateInYear(year)
        val previousYearDate = dateInYear(year - 1)
        val records = buildList {
            repeat(9) { index ->
                add(record(id = index.toLong(), amount = 1_000, direction = GiftDirection.RECEIVED, date = currentYearDate))
            }
            add(record(id = 10, amount = 2_500, direction = GiftDirection.GIVEN, date = currentYearDate))
            add(record(id = 11, amount = 99_900, direction = GiftDirection.RECEIVED, date = previousYearDate))
            add(record(id = 12, amount = 88_800, direction = GiftDirection.RECEIVED, date = dateInYear(year + 1)))
        }
        val viewModel = HomeViewModel(TestContactRepository(emptyList()), TestGiftRepository(records))
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(9_000L, viewModel.uiState.value.received)
        assertEquals(2_500L, viewModel.uiState.value.given)
        assertEquals(6_500L, viewModel.uiState.value.net)
        assertEquals(8, viewModel.uiState.value.recentRecords.size)
        assertEquals(listOf(year, year - 1), viewModel.uiState.value.availableYears)
        assertFalse(viewModel.uiState.value.availableYears.contains(year + 1))

        viewModel.selectYear(year - 1)
        advanceUntilIdle()
        assertEquals(year - 1, viewModel.uiState.value.year)
        assertEquals(99_900L, viewModel.uiState.value.received)
        assertEquals(0L, viewModel.uiState.value.given)
        collection.cancel()
    }

    @Test
    fun `联系人支持查询并按姓名排序`() = runTest(dispatcher) {
        val summaries = listOf(summary(2, "张三"), summary(1, "阿姨"), summary(3, "李四"))
        val viewModel = ContactsViewModel(TestContactRepository(summaries))
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.updateSort(ContactSort.NAME)
        advanceUntilIdle()
        assertEquals(listOf("阿姨", "李四", "张三"), viewModel.uiState.value.contacts.map { it.contact.name })

        viewModel.updateQuery("李")
        advanceUntilIdle()
        assertEquals(listOf("李四"), viewModel.uiState.value.contacts.map { it.contact.name })
        collection.cancel()
    }

    @Test
    fun `联系人最近往来按最后礼金日期倒序而不是姓名顺序`() = runTest(dispatcher) {
        val summaries = listOf(
            summary(1, "赵阿姨", lastInteractionTime = 100),
            summary(2, "陈叔叔", lastInteractionTime = 300),
            summary(3, "王同学", lastInteractionTime = 200),
        )
        val viewModel = ContactsViewModel(TestContactRepository(summaries))
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        assertEquals(
            listOf("陈叔叔", "王同学", "赵阿姨"),
            viewModel.uiState.value.contacts.map { it.contact.name },
        )
        collection.cancel()
    }

    @Test
    fun `提醒页展示未来三十天年度日期并同步开关状态`() = runTest(dispatcher) {
        val utc = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JULY, 21, 10, 0, 0)
        }.timeInMillis
        val eventDate = Calendar.getInstance(utc).apply {
            clear()
            set(2024, Calendar.JULY, 25, 0, 0, 0)
        }.timeInMillis
        val reminders = TestReminderRepository()
        val viewModel = NotificationsViewModel(
            TestGiftRepository(listOf(record(20, 20_000, GiftDirection.RECEIVED, eventDate))),
            reminders,
            now = { now },
            timeZone = utc,
        )
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        advanceUntilIdle()
        assertEquals(listOf(20L), viewModel.uiState.value.upcoming.map { it.record.id })
        assertFalse(viewModel.uiState.value.remindersEnabled)

        viewModel.setRemindersEnabled(true)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.remindersEnabled)

        viewModel.updateReminderSchedule(3, 8, 30)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.reminderAdvanceDays)
        assertEquals(8, viewModel.uiState.value.reminderHour)
        assertEquals(30, viewModel.uiState.value.reminderMinute)
        collection.cancel()
    }

    @Test
    fun `联系人不存在时详情页进入安全空状态`() = runTest(dispatcher) {
        val viewModel = ContactDetailViewModel(
            404,
            TestContactRepository(emptyList()),
            TestGiftRepository(emptyList()),
        )
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.notFound)
        assertFalse(viewModel.uiState.value.error)
        collection.cancel()
    }

    private fun dateInYear(year: Int) = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 2, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun record(id: Long, amount: Long, direction: GiftDirection, date: Long) = GiftRecordWithContact(
        GiftRecord(id, 1, amount, EventType.OTHER, date, direction),
        "联系人",
    )

    private fun summary(
        id: Long,
        name: String,
        lastInteractionTime: Long = 0,
    ) = ContactLedgerSummary(
        contact = Contact(id = id, name = name),
        receivedInCents = 0,
        givenInCents = 0,
        lastInteractionTime = lastInteractionTime,
    )
}

private class TestReminderRepository : ReminderRepository {
    override val settings = MutableStateFlow(ReminderSettings())
    override fun setEnabled(enabled: Boolean) { settings.value = settings.value.copy(enabled = enabled) }
    override fun updateSchedule(advanceDays: Int, hour: Int, minute: Int) {
        settings.value = settings.value.copy(advanceDays = advanceDays, hour = hour, minute = minute)
    }
    override fun synchronize(records: List<GiftRecordWithContact>) = Unit
}

private class TestContactRepository(private val summaries: List<ContactLedgerSummary>) : ContactRepository {
    override suspend fun previewDelete(ids: Set<Long>): com.yangsong.lizhang.domain.model.ContactDeletePreview = error("此测试不执行批量删除")
    override suspend fun deleteContacts(ids: Set<Long>, confirmed: com.yangsong.lizhang.domain.model.ContactDeletePreview): com.yangsong.lizhang.domain.model.ContactBulkDeleteOutcome = error("此测试不执行批量删除")
    override suspend fun importDeviceContacts(selections: List<com.yangsong.lizhang.domain.model.ContactImportSelection>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    override suspend fun createAll(contacts: List<Contact>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    override fun observeContacts(query: String): Flow<List<Contact>> =
        flowOf(summaries.map { it.contact }.filter { it.name.contains(query, ignoreCase = true) })

    override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> =
        flowOf(summaries.filter { it.contact.name.contains(query, ignoreCase = true) || it.contact.phone.orEmpty().contains(query) })

    override fun observeContact(contactId: Long): Flow<Contact?> =
        flowOf(summaries.firstOrNull { it.contact.id == contactId }?.contact)

    override suspend fun create(contact: Contact): Long = contact.id
    override suspend fun update(contact: Contact) = Unit
    override suspend fun delete(contact: Contact) = Unit
}

private class TestGiftRepository(private val records: List<GiftRecordWithContact>) : GiftRecordRepository {
    override fun observeRecent(limit: Int): Flow<List<GiftRecordWithContact>> = flowOf(records.take(limit))
    override fun observeAll(): Flow<List<GiftRecordWithContact>> = flowOf(records)
    override fun observeByDirection(direction: GiftDirection): Flow<List<GiftRecordWithContact>> = flowOf(records.filter { it.record.direction == direction })
    override fun observeRecord(recordId: Long): Flow<GiftRecord?> = flowOf(records.firstOrNull { it.record.id == recordId }?.record)
    override fun observeRecordWithContact(recordId: Long): Flow<GiftRecordWithContact?> = flowOf(records.firstOrNull { it.record.id == recordId })
    override fun observeByContact(contactId: Long): Flow<List<GiftRecord>> = flowOf(records.map { it.record }.filter { it.contactId == contactId })
    override fun observeSearch(query: String): Flow<List<GiftRecordWithContact>> = flowOf(records.filter { it.contactName.contains(query, ignoreCase = true) })
    override suspend fun create(record: GiftRecord): Long = record.id
    override suspend fun update(record: GiftRecord) = Unit
    override suspend fun delete(record: GiftRecord) = Unit
}
